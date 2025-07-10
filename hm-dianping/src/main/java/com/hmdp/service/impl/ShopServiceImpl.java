package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Objects;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryById(Long id) {
        String key = "CACHE_SHOP_KEY" + id;
        //1. 从redis中查询店铺数据
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        Shop shop = null;
        //2. 判断缓存是否命中
        if (StrUtil.isNotBlank(shopJson)) {
            shop = JSONUtil.toBean(shopJson, Shop.class);
            return Result.ok(shop);
        }
        //3. 缓存未命中，查询数据库店铺数据
        shop = this.getById(id);

        //4. 判断数据库是否存在店铺数据
        if (Objects.isNull(shop)){
            //5. 不存在，返回错误信息
            return Result.fail("店铺不存在");
        }
        //6. 数据库中存在，写入缓存并返回店铺数据
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop));
        return Result.ok(shop);
    }
    /**
     * 更新商铺数据（更新时，更新数据库，删除缓存）
     *
     * @param shop
     * @return
     */
    @Transactional
    @Override
    public Result updateShop(Shop shop) {
       // 1. 更新数据库中的店铺数据
        boolean update = this.updateById(shop);
        if (!update) {
            // 2. 更新失败，返回错误信息
            return Result.fail("数据库更新失败");
        }
        // 3. 删除缓存中的店铺数据
       update = stringRedisTemplate.delete("CACHE_SHOP_KEY" + shop.getId());
        if (!update) {
            // 4. // 缓存删除失败，抛出异常，事务回滚
            throw new RuntimeException("缓存删除失败");
        }
        return Result.ok();
    }
}
