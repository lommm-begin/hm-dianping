package com.hmdp.scheduled;

import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import jakarta.annotation.Resource;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.hmdp.utils.RedisConstants.BLOOM_FILTER_KEY;

@Component
public class BloomFilterScheduledTask {

    RBloomFilter<Object> bloomFilter;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private ShopServiceImpl shopService;

    // 每天凌晨三点重建布隆过滤器
    @Scheduled(cron = "0 0 3 * * ?")
    public void bloomFilter() {
        bloomFilter = redissonClient.getBloomFilter(BLOOM_FILTER_KEY);
        if (bloomFilter.isExists()) {
            bloomFilter.delete();
        }
        List<Long> list = shopService.findAll()
                .stream()
                .map(Shop::getId)
                .toList();
        list.forEach(bloomFilter::add);
    }
}
