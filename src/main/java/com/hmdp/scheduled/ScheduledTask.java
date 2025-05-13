package com.hmdp.scheduled;

import com.hmdp.entity.Shop;
import com.hmdp.mq.product.Product;
import com.hmdp.service.impl.ShopServiceImpl;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.hmdp.utils.constants.RedisConstants.*;
import static com.hmdp.utils.constants.RedisConstants.BLOG_LIKED_COUNT_KEY;

@Component
@Slf4j
public class ScheduledTask {
    private boolean stop = false;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private ShopServiceImpl shopService;

    @Resource
    private Product product;

    // 每天凌晨三点重建布隆过滤器
    @Scheduled(cron = "0 0 3 * * ?")
    public void bloomFilter() {
        RBloomFilter<Object> bloomFilter = redissonClient.getBloomFilter(BLOOM_FILTER_KEY);
        if (bloomFilter.isExists()) {
            bloomFilter.delete();
        }
        List<Long> list = shopService.findAll()
                .stream()
                .map(Shop::getId)
                .toList();
        list.forEach(bloomFilter::add);
    }

    // 参数是必须返回了再开始计时下一次的定时任务
    @Scheduled(fixedDelay = 5000)
    public void updateLikes() {
        try {
            for (Long size = stringRedisTemplate.opsForSet().size(BLOG_LIKED_USER_KEY);
                 !stop && size != null && size > 0;) {

                String blogId = stringRedisTemplate.opsForSet().pop(BLOG_LIKED_USER_KEY);

                // 发送到消息队列
                product.send(
                        "exchange_spring",
                        "rowKey_likeUpdate",
                        "updateLikeCount",
                        blogId,
                        RETRY_PRE_KEY + BLOG_LIKED_COUNT_KEY + blogId
                );
            }
        } catch (Exception e) {
            log.error("同步数据库点赞数量发生异常，已捕获处理: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void setStop() {
        stop = true;
    }
}
