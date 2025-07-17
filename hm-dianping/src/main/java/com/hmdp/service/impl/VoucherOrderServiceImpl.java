package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RedissonClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Collections;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */

@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;
    /**
     * 秒杀优惠券
     * @param voucherId
     * @return
     */




    /**
     * 加载Lua脚本
     */
    private static final DefaultRedisScript<Long> SECKILL_SCKIPT;

    static {
        SECKILL_SCKIPT = new DefaultRedisScript<>();
        SECKILL_SCKIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCKIPT.setResultType(Long.class);
    }
    /**
     * 抢购秒杀券
     *
     * @param voucherId
     * @return
     */
    @Override
    public Result seckillVoucher(Long voucherId) {
        // 获取用户
        Long userId = UserHolder.getUser().getId();
        // 1.执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCKIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString()
        );
        // 2.判断结果是否为0
        int resultInt = result.intValue();
        if (resultInt != 0){
            // 2.1 不为0,代表没有购买资格
            return Result.fail(result == 1 ? "库存不足" : "不能重复下单");
        }
        // 2.2 为0,有购买资格,把下单信息保存到阻塞队列
        long orderId = redisIdWorker.nextId("order");
        // 3.返回订单id
        return Result.ok(orderId);

    }
   /* @Override
    public Result seckillVoucher(Long voucherId) {
        // 1. 查询优惠券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        // 检查优惠券是否为空
        if (voucher == null) {
            return Result.fail("优惠券不存在");
        }
        // 2. 判断秒杀券是否合法...
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            // 秒杀券的开始时间在当前时间之后
            return Result.fail("秒杀券尚未开始");
        }
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            // 秒杀券的结束时间在当前时间之前
            return Result.fail("秒杀券已结束");
        }
        if (voucher.getStock() < 1) {
            // 秒杀券的库存不足
            return Result.fail("秒杀券已售罄");
        }
        // 3.创建订单(使用分布式锁)
        Long userId = UserHolder.getUser().getId();
       // SimpleRedisLock lock = new SimpleRedisLock("order:" + userId , stringRedisTemplate);
        RLock lock = redissonClient.getLock("lock:order: " + userId);
        // 获取锁成功
        Boolean isLock = lock.tryLock();
        if (!isLock) {
            // 获取锁失败，返回失败结果(这个业务是一人一单,锁的粒度应该为用户,所以直接返回失败信息)
            return Result.fail("一人只能下一单");
        }
        try {
            // 获取锁成功,创建代理对象,使用代理对象调用第三事务方法,防止事务失效
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.creatVoucherOrder(voucherId, userId);
        }finally {
            lock.unlock();
        }

    }*/

    /**
     * 创建订单
     *
     * @param voucherId
     * @return
     */
    @Transactional
    public Result creatVoucherOrder(Long voucherId,Long userId) {
        //        synchronized (userId.toString().intern()) {
        // 1、判断当前用户是否是第一单
        int count = this.count(new LambdaQueryWrapper<VoucherOrder>()
                .eq(VoucherOrder::getUserId, userId));
        if (count >= 1) {
            // 当前用户不是第一单
            return Result.fail("用户已购买");
        }
        // 2、用户是第一单，可以下单，秒杀券库存数量减一
        boolean flag = seckillVoucherService.update(new LambdaUpdateWrapper<SeckillVoucher>()
                .eq(SeckillVoucher::getVoucherId, voucherId)
                .gt(SeckillVoucher::getStock, 0)
                .setSql("stock = stock -1"));
        if (!flag) {
            throw new RuntimeException("秒杀券扣减失败");
        }
        // 3、创建对应的订单，并保存到数据库
        VoucherOrder voucherOrder = new VoucherOrder();
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(UserHolder.getUser().getId());
        voucherOrder.setVoucherId(voucherOrder.getId());
        flag = this.save(voucherOrder);
        if (!flag) {
            throw new RuntimeException("创建秒杀券订单失败");
        }
        // 4、返回订单id
        return Result.ok(orderId);
        }


         /*@Override
    @Transactional
    public Result seckillVoucher(Long voucherId) {
     // 1.查询优惠券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        // 2.判断秒杀券是否合法
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())){
            // 秒杀券的开始时间在当前时间之后
            return Result.fail("秒杀券尚未开始");
        }
        if (voucher.getEndTime().isBefore(LocalDateTime.now())){
            // 秒杀券的结束时间在当前时间之前
            return Result.fail("秒杀券已结束");
        }
        if (voucher.getStock() < 1){
            // 秒杀券的库存不足
            return Result.fail("秒杀券已售罄");
        }
        // 3. 秒杀券合法,则秒杀券抢购成功,秒杀券库存数量-1
        boolean flag = seckillVoucherService.update(new LambdaUpdateWrapper<SeckillVoucher>()
                .eq(SeckillVoucher::getVoucherId, voucherId)
                .setSql("stock = stock -1"));
        if (!flag){
            throw new RuntimeException("库存扣减失败");
        }
        // 4.秒杀成功,创建对应的订单,并保存到数据库
        VoucherOrder voucherOrder = new VoucherOrder();
        // 生成订单id
        long oerderId = redisIdWorker.nextId("SECKILL_VOUCHER_ORDER");
        voucherOrder.setId(oerderId);
        // 获取用户id
        Long UserId = UserHolder.getUser().getId();
        // 设置用户id
        voucherOrder.setUserId(UserId);
        // 设置优惠券id
        voucherOrder.setVoucherId(voucherId);
        // 保存订单voucherId
        save(voucherOrder);
        flag = this.save(voucherOrder);
        if (!flag){
            throw new RuntimeException("创建秒杀券订单失败");
        }
        return Result.ok(oerderId);
    }*/

}
