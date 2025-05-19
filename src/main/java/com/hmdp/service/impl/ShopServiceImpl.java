package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.mq.product.Product;
import com.hmdp.service.IShopService;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.SystemConstants;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.data.redis.domain.geo.Metrics;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.constants.RedisConstants.*;

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
    @Cacheable(value = "shop", key = "T(com.hmdp.utils.constants.RedisConstants).CACHE_SHOP_KEY + #id")
    @Override
    public Result queryById(Long id) {

        Shop shop = cacheClient.queryByIdFromRedis(
                CACHE_SHOP_KEY,
                id,
                Shop.class,
                RETRY_PRE_KEY + CACHE_SHOP_KEY + id,
                CACHE_SHOP_TTL,
                CACHE_MAX_TTL,
                CACHE_MIN_TTL,
                TimeUnit.SECONDS);

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

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y, Double distance) {

        // 判断是否需要按坐标查询
        if (x == null || y == null) {
            // 不需要
            // 根据类型分页查询
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            // 返回数据
            return Result.ok(page.getRecords());
        }

        // 计算分页参数
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int pageSize = current * SystemConstants.DEFAULT_PAGE_SIZE; // 因为从redis获取的时候只会从0开始到指定的结尾

        // 查询redis 根据距离，排序。geosearch key bylonlat byradius current withdistance
        String key = SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> geoResults = stringRedisTemplate.opsForGeo().search(
                key,
                GeoReference.fromCoordinate(x, y),
                new Distance(5000, Metrics.METERS), // distance
                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs()
                        .limit(pageSize)
        );

        // 获取真正数据
        if (geoResults == null) {
            return Result.ok();
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> content = geoResults.getContent();

        // 将店铺id和距离封装一起
        List<Long> ids = new ArrayList<>(content.size());

        Map<String, Distance> distanceMap = new HashMap<>(content.size());

        // 判断是否还有数据
        if (content.size() <= from) {
            return Result.ok();
        }
        content.stream().skip(from).forEach(geoResult -> {
            String shopId = geoResult.getContent().getName();
            ids.add(Long.valueOf(shopId));
            distanceMap.put(shopId, geoResult.getDistance());
        });

        // 从数据库根据id获取对应店铺的信息
        String join = StrUtil.join(",", ids);
        List<Shop> list = query().in("id", ids).last("order by field(id, " + join + ")").list();

        // 给店铺的距离赋值
        list.forEach(shop -> shop.setDistance(distanceMap.get(shop.getId().toString()).getValue()));

        return Result.ok(list);
    }

    @CacheEvict(value = "shop", key = "T(com.hmdp.utils.constants.RedisConstants).CACHE_SHOP_KEY + #id")
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
