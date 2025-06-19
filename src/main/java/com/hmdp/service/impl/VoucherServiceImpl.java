package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Voucher;
import com.hmdp.mapper.VoucherMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherService;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.constants.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherServiceImpl extends ServiceImpl<VoucherMapper, Voucher> implements IVoucherService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ObjectMapper objectMapper;

    @Override
    public Result queryVoucherOfShop(Long shopId) {
        // 在redis查询优惠券信息
        List<Voucher> allVoucher = getVoucherFromRedis(shopId);
        if (allVoucher != null && !allVoucher.isEmpty()) {
                // 存在，则返回 redis 的数据
                return Result.ok(allVoucher);
        }

        // 不存在，查询数据库
        List<Voucher> vouchers = getBaseMapper().queryVoucherOfShop(shopId);

        // 数据库查询为空
        if (vouchers.isEmpty()) {
            // 随便缓存一个空数据
            stringRedisTemplate.opsForValue().set(
                    VOUCHER_SHOP_KEY + shopId,
                    "",
                    1,
                    TimeUnit.MINUTES);
            return Result.ok();
        }

        // 同步redis
        saveToRedis(vouchers);

        // 返回结果
        return Result.ok(vouchers);
    }

    private void saveToRedis(List<Voucher> vouchers) {
        vouchers.forEach(voucher -> {
            try {
                String key = VOUCHER_SHOP_KEY + voucher.getId();
                // 依次保存到redis
                stringRedisTemplate.opsForValue().set(
                        key,
                        objectMapper.writeValueAsString(voucher),
                        30 * 24 * 60 * 60,
                        TimeUnit.SECONDS);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private List<Voucher> getVoucherFromRedis(Long shopId) {
        Set<String> keys = stringRedisTemplate.keys(VOUCHER_SHOP_KEY + "*");

        if (keys.isEmpty()) {
            return null;
        }

        return stringRedisTemplate.opsForValue().multiGet(keys).stream()
                .map(voucher -> {
                    try {
                        Voucher v = objectMapper.readValue(voucher, Voucher.class);

                        if (v.getShopId() != shopId) {
                            return null;
                        }

                        return v;
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                })
                .toList();
    }

    @Override
    @Transactional
    public void addSeckillVoucher(Voucher voucher) {
        // 保存优惠券
        save(voucher);
        // 保存秒杀信息
        SeckillVoucher seckillVoucher = new SeckillVoucher();
        seckillVoucher.setVoucherId(voucher.getId());
        seckillVoucher.setStock(voucher.getStock());
        seckillVoucher.setBeginTime(voucher.getBeginTime());
        seckillVoucher.setEndTime(voucher.getEndTime());
        seckillVoucherService.save(seckillVoucher);

        saveSeckillVoucher(voucher);
    }

    private void saveSeckillVoucher(Voucher voucher) {
        // 保存秒杀优惠券库存到redis
        stringRedisTemplate.opsForHash()
                .putAll(SECKILL_STOCK_KEY + voucher.getId(),
                        Map.of("status", voucher.getStatus(),
                                "stock", voucher.getStock(),
                                "beginTime", voucher.getBeginTime().toEpochSecond(ZoneOffset.UTC),
                                "endTime", voucher.getEndTime().toEpochSecond(ZoneOffset.UTC)));

        try {
            String key = VOUCHER_SHOP_KEY + voucher.getId();

            // 保存完整信息到redis
            stringRedisTemplate.opsForValue().set(
                    key,
                    objectMapper.writeValueAsString(voucher),   // 设置结束时间为过期时间
                    voucher.getEndTime().toEpochSecond(ZoneOffset.UTC) - LocalDateTime.now().toEpochSecond(ZoneOffset.UTC),
                    TimeUnit.SECONDS);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
