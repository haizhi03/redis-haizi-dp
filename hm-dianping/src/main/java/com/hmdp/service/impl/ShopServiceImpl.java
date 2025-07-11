package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

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

    /**
     * 缓存重建线程池
     */
    public static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     * 根据id查询商铺数据
     *
     * @param id
     * @return
     */
    @Override
    public Result queryById(Long id) {
        String key = CACHE_SHOP_KEY + id;
        // 1、从Redis中查询店铺数据，并判断缓存是否命中
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isBlank(shopJson)) {
            // 1.1 缓存未命中，直接返回失败信息
            return Result.fail("店铺数据不存在");
        }
        // 1.2 缓存命中，将JSON字符串反序列化未对象，并判断缓存数据是否逻辑过期
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        // 这里需要先转成JSONObject再转成反序列化，否则可能无法正确映射Shop的字段
        JSONObject data = (JSONObject) redisData.getData();
        Shop shop = JSONUtil.toBean(data, Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 当前缓存数据未过期，直接返回
            return Result.ok(shop);
        }

        // 2、缓存数据已过期，获取互斥锁，并且重建缓存
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        if (isLock) {
            // 获取锁成功，开启一个子线程去重建缓存
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    this.saveShopToCache(id, 20L);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } finally {
                    unlock(lockKey);
                }
            });
        }

        // 3、获取锁失败，再次查询缓存，判断缓存是否重建（这里双检是有必要的）
        shopJson = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isBlank(shopJson)) {
            // 3.1 缓存未命中，直接返回失败信息
            return Result.fail("店铺数据不存在");
        }
        // 3.2 缓存命中，将JSON字符串反序列化未对象，并判断缓存数据是否逻辑过期
        redisData = JSONUtil.toBean(shopJson, RedisData.class);
        // 这里需要先转成JSONObject再转成反序列化，否则可能无法正确映射Shop的字段
        data = (JSONObject) redisData.getData();
        shop = JSONUtil.toBean(data, Shop.class);
        expireTime = redisData.getExpireTime();
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 当前缓存数据未过期，直接返回
            return Result.ok(shop);
        }

        // 4、返回过期数据
        return Result.ok(shop);
    }

    /**
     * 将数据保存到缓存中
     *
     * @param id            商铺id
     * @param expireSeconds 逻辑过期时间
     */
    public void saveShopToCache(Long id, Long expireSeconds) throws InterruptedException {
        // 从数据库中查询店铺数据
        Shop shop = this.getById(id);
        Thread.sleep(200);
        // 封装逻辑过期数据
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        // 将逻辑过期数据存入Redis中
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 获取锁
     *
     * @param key
     * @return
     */
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        // 拆箱要判空，防止NPE
        return BooleanUtil.isTrue(flag);
    }

    /**
     * 释放锁
     *
     * @param key
     */
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }

    /**
     * 从缓存中获取店铺数据
     * @parm key
     * @return
     */
    private Result getShopFromCache(String key){
        //1. 从redis中查询店铺数据
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        Shop shop = null;
        //2. 判断缓存是否命中

        if (StrUtil.isNotBlank(shopJson)) {
            //  缓存数据有值, 说明缓存命中了,直接返回店铺数据
            shop = JSONUtil.toBean(shopJson, Shop.class);
            return Result.ok(shop);
        }
        // 2.1 缓存未命中,判断缓存中查询的数据是否为空字符串(isnotBlank把null和空字符串给排除了)
        if (Objects.nonNull(shopJson)){
            // 2.1.1 缓存中查询的数据为空字符串，返回错误信息
            return Result.fail("店铺不存在");
        }
        // 缓存未命中(缓存数据没有值,又不是空字符串)
        return null;
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
            return Result.fail("店铺id不能为空");
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
