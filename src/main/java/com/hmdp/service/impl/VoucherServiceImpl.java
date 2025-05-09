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

import static com.hmdp.utils.RedisConstants.SECKILL_STOCK_KEY;
import static com.hmdp.utils.RedisConstants.VOUCHER_KEY;

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

    public static final String EMPTY_KEY = VOUCHER_KEY + "empty:0";

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ObjectMapper objectMapper;

    @Override
    public Result queryVoucherOfShop(Long shopId) {
        // 在redis查询优惠券信息
        List<String> allVoucher = getVoucherFromRedis();
        if (allVoucher != null && allVoucher.size() == 1) {
            if (allVoucher.get(0).startsWith(EMPTY_KEY)) {
                // 存在，则返回 redis 的数据
                return Result.ok(allVoucher);
            }
        }

        // 不存在，查询数据库
        List<Voucher> vouchers = getBaseMapper().queryVoucherOfShop(shopId);

        // 查询为空
        if (vouchers.isEmpty()) {
            // 随便缓存一个空数据
            stringRedisTemplate.opsForValue().set(
                    EMPTY_KEY,
                    "",
                    3,
                    TimeUnit.MINUTES);
            return Result.fail("优惠券不存在!");
        }

        // 同步redis
        saveToRedis(vouchers);

        // 返回结果
        return Result.ok(vouchers);
    }

    private void saveToRedis(List<Voucher> vouchers) {
        vouchers.forEach(voucher -> {
            try {
                String key = VOUCHER_KEY + voucher.getId();
                // 依次保存到redis
                stringRedisTemplate.opsForValue().set(
                        key,
                        objectMapper.writeValueAsString(voucher),
                        voucher.getEndTime().toEpochSecond(ZoneOffset.UTC)
                                - LocalDateTime.now().toEpochSecond(ZoneOffset.UTC),
                        TimeUnit.SECONDS);
                stringRedisTemplate.delete(EMPTY_KEY);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private List<String> getVoucherFromRedis() {
        Set<String> keys = stringRedisTemplate.keys(VOUCHER_KEY + "*");

        // 无需再次反序列化
        return stringRedisTemplate.opsForValue().multiGet(keys);
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

        // 保存秒杀优惠券库存到redis
        stringRedisTemplate.opsForHash()
                .putAll(SECKILL_STOCK_KEY + voucher.getId(),
                        Map.of("status", voucher.getStatus(),
                                "stock", voucher.getStock(),
                                "beginTime", voucher.getBeginTime().toEpochSecond(ZoneOffset.UTC),
                                "endTime", voucher.getEndTime().toEpochSecond(ZoneOffset.UTC)));

        try {
            String key = VOUCHER_KEY + voucher.getId();

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
