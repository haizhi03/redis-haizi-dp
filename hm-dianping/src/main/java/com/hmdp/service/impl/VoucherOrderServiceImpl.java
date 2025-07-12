package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import io.netty.handler.codec.marshalling.ThreadLocalUnmarshallerProvider;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

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
    /**
     * 秒杀优惠券
     * @param voucherId
     * @return
     */

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
    @Transactional
    @Override
    public Result seckillVoucher(Long voucherId) {
        // 1. 查询优惠券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        // 2. 判断秒杀券是否合法...
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀券尚未开始");
        }
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀券已结束");
        }
        if (voucher.getStock() < 1) {
            return Result.fail("秒杀券已售罄");
        }

        // 新增：检查用户是否已经抢过该优惠券
        Long userId = UserHolder.getUser().getId();
        int count = this.count(new LambdaQueryWrapper<VoucherOrder>()
                .eq(VoucherOrder::getUserId, userId)
                .eq(VoucherOrder::getVoucherId, voucherId));
        if (count > 0) {
            return Result.fail("您已抢购过该优惠券");
        }

        // 3. 扣减库存
        boolean flag = seckillVoucherService.update(new LambdaUpdateWrapper<SeckillVoucher>()
                .eq(SeckillVoucher::getVoucherId, voucherId)
                .setSql("stock = stock - 1"));
        if (!flag) {
            throw new RuntimeException("库存扣减失败");
        }

        // 4. 创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        long orderId = redisIdWorker.nextId("SECKILL_VOUCHER_ORDER");
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        flag = this.save(voucherOrder);
        if (!flag) {
            throw new RuntimeException("创建秒杀券订单失败");
        }
        return Result.ok(orderId);
    }

}
