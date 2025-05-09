package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.mq.product.Product;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import com.hmdp.utils.VoucherStatusType;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.Map;

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
@Slf4j
public class VoucherOrderServiceImplByRabbitmq extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private Product product;

    // 校验订单是否存在
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill2.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }


    // 使用lua脚本
    @Override
    public Result seckillVoucher(Long voucherId) {
        Long executed = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                UserHolder.getUser().getId().toString(),
                String.valueOf(LocalDateTime.now().toEpochSecond(ZoneOffset.UTC))
        );

        int r = executed.intValue();

        // 检验操作
        if (r != SUCCESS_STATUS) {
            // 返回对应的错误信息
            return Result.fail(VoucherStatusType.voucherStatusType(r));
        }

        // 生成订单ID
        long orderId = redisIdWorker.nextId(ORDER_PREFIX_KEY);
        Long userId = UserHolder.getUser().getId();

        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setUserId(userId);
        voucherOrder.setId(orderId);

        String exchange = "exchange_spring";
        String rowKey = "rowKey_spring";
        String key = RETRY_PRE_KEY + VOUCHER_KEY + userId;

        try {
            // 存入redis
            boolean isSuccess = (boolean) stringRedisTemplate.execute(new SessionCallback<Object>() {
                @Override
                public Object execute(RedisOperations operations) throws DataAccessException {
                    boolean isSuccess = false;
                    operations.multi();
                    try {
                        operations.opsForHash().putAll(key, Map.of(
                                "exchange", exchange,
                                "rowKey", rowKey,
                                "userId", userId,
                                "voucherId", voucherId,
                                "orderId", String.valueOf(orderId),
                                "retryCount", 0
                        ));

                        // 设置过期时间
                        stringRedisTemplate.expire(key, Duration.ofSeconds(RETRY_TTL));
                        operations.exec();
                        isSuccess = true;
                    } catch (Exception e) {
                        operations.discard();
                        log.error("存入优惠券重试信息时发生错误: {}", e.getMessage());
                    }

                    return isSuccess;
                }
            });

            if (isSuccess) {
                // 放入rabbitmq
                product.send(exchange, rowKey, voucherOrder, key);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return Result.ok(orderId);
    }

    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        Long count = query()
                .eq("user_id", voucherOrder.getUserId())
                .eq("voucher_id", voucherOrder.getVoucherId())
                .count();

        if (count > 0) {
            log.error("已经获取过了！");
        }

        // 扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherOrder.getVoucherId())
                .gt("stock", 0)
                .update();

        if (!success) {
            log.error("优惠券已经被抢光了");
        }

        // 保存订单
        save(voucherOrder);

        // 保存到redis
        stringRedisTemplate.opsForHash().putAll(SECKILL_STOCK_KEY + voucherOrder.getUserId(),
                BeanUtil.beanToMap(voucherOrder, false, true));
    }

//    @Override
//    public Result seckillVoucher2(Long voucherId) {
//        // 查询优惠券
//        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
//
//        // 秒杀活动是否开始
//        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
//            return Result.fail("秒杀活动未开始！");
//        }
//
//        // 秒杀是否结束
//        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
//            return Result.fail("秒杀活动已经结束！");
//        }
//
//        // 库存是否充足
//        if (voucher.getStock() < 1) {
//            return Result.fail("优惠券已经被抢光了~");
//        }
//        Long id = UserHolder.getUser().getId();
//
////        synchronized (id.toString().intern()) { // 防止每次都是新的引用，去常量池找同一个值
////            IVoucherOrderService iVoucherOrderService = (IVoucherOrderService) AopContext.currentProxy();
////            return iVoucherOrderService.createVoucherOrder(id, voucherId);
////        }
//        SimpleRedisLock lock = new SimpleRedisLock(stringRedisTemplate, SECKILL_LOCK_KEY + id);
//
//        RLock rLock = redissonClient.getLock(SECKILL_LOCK_KEY + id);
//
//        boolean isLock = rLock.tryLock();
//        if (!isLock) {
//            // 获取锁失败
//            return Result.fail("不允许重复下单");
//        }
//        try {
//            IVoucherOrderService iVoucherOrderService = (IVoucherOrderService) AopContext.currentProxy();
//            return iVoucherOrderService.createVoucherOrder(voucherId);
//        } finally {
//            rLock.unlock();
//        }
//    }
}
