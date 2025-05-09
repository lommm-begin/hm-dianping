package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.mq.product.Product;
import com.hmdp.service.IShopService;
import com.hmdp.utils.CacheClient;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    RBloomFilter<Object> bloomFilter;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private CacheClient cacheClient;

    @Resource
    private Product product;

    // 在控制器调用布隆过滤器进行验证
    @PostConstruct
    private void init() {
        bloomFilter = redissonClient.getBloomFilter(BLOOM_FILTER_KEY);
        if (!bloomFilter.isExists()) {
            bloomFilter.tryInit(50000, 0.01);
        }

        List<Long> list = query().list()
                .stream()
                .map(Shop::getId)
                .toList();
        list.forEach(bloomFilter::add);
    }

    public List<Shop> findAll() {
        return query().list();
    }

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // 底层实现是 Caffeine
//    @Cacheable(value = "shop", key = "T(com.hmdp.utils.RedisConstants).CACHE_SHOP_KEY + #id")
    @Override
    public Result queryById(Long id) {

        Shop shop = cacheClient.queryShopByIdFromRedis(
                CACHE_SHOP_KEY,
                id,
                Shop.class,
                this::getById,
                CACHE_SHOP_TTL,
                CACHE_SHOP_PER_MILLISECONDS,
                CACHE_SHOP_MIN_MILLISECONDS,
                TimeUnit.MILLISECONDS);

        return Result.ok(shop);
    }

    @Transactional
    @Override
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺ID不能为空");
        }
        // 第一次删除
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);

        // 更新数据库
        updateById(shop);

        // 第二次删除缓存
        product.sendDelayedTask(
            "exchange_spring",
                "rowKey_delay",
                CACHE_SHOP_KEY + id,
                RETRY_PRE_KEY + CACHE_SHOP_KEY + id,
                ThreadLocalRandom.current().nextLong(DELAY_MIN_MILLIS, DELAY_MAX_MILLIS)
        );

        return Result.ok();
    }

    @CacheEvict(value = "shop", key = "T(com.hmdp.utils.RedisConstants).CACHE_SHOP_KEY + #id")
    public void deleteShop(Long id) {
    }

    //    private boolean tryLock(String key) {
//        // setnx
//        Boolean b = stringRedisTemplate.opsForValue().setIfAbsent(
//                key,
//                Thread.currentThread().getName(),
//                LOCK_SHOP_TTL,
//                TimeUnit.SECONDS);
//
//        return Optional.of(b).orElse(false);
//    }
//
//    private void unlock(String key) {
//        stringRedisTemplate.delete(key);
//    }
}
