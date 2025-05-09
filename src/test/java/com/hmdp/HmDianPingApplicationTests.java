package com.hmdp;

import com.hmdp.mq.product.Product;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisIdWorker;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    CacheClient cacheClient;

    @Resource
    ShopServiceImpl shopService;

    @Resource
    RedisIdWorker redisIdWorker;

    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Resource
    Product product;

    @Test
    public void contextLoads() {

    }

    @Test
    public void testShopService() {
        for (int i = 1; i <= 14; i++) {
        }
    }

    @Test
    public void testShopService2() {
        cacheClient.setWithLogicalExpire(
                CACHE_SHOP_KEY + 1,
                shopService.getById(1),
                5000000,
                TimeUnit.SECONDS
        );
    }

    @Test
    public void testShopService3() throws IOException {

        for (int i = 0; i < 30; i++) {
            CompletableFuture.runAsync(() -> {
                for (int j = 0; j < 100; j++) {
                    long l = redisIdWorker.nextId(ORDER_PREFIX_KEY);
                    System.out.println("l = " + l);
                }
            });
        }

        System.in.read();
    }

    @Test
    public void testShopService4() throws IOException {
        // 保存秒杀优惠券到redis
        stringRedisTemplate.opsForHash()
                .putAll(SECKILL_STOCK_KEY + 12,
                        Map.of("status", 1,
                                "stock", 100,
                                "beginTime", LocalDateTime.now().toEpochSecond(ZoneOffset.UTC),
                                "endTime", LocalDateTime.now().plusSeconds(10000000).toEpochSecond(ZoneOffset.UTC)));
    }

    // 测试延迟队列
    @Test
    public void testShopService5() throws IOException, InterruptedException {
        long id = 1111;
        System.out.println(LocalDateTime.now());
        product.sendDelayedTask(
                "delayed_exchange",
                "rowKey_delay",
                CACHE_SHOP_KEY + id,
                RETRY_PRE_KEY + CACHE_SHOP_KEY + id,
                1000L * 15
        );

        System.in.read();
    }

    @Test
    public void testmq() {
        System.out.println(LocalDateTime.now());
        product.sendDelayedTask(
                "delayed_exchange",
                "rowKey_delay",
                CACHE_SHOP_KEY + "2222",
                RETRY_PRE_KEY + CACHE_SHOP_KEY + "2222",
                1000L * 15
        );
    }

    @Test
    public void testShopService6() throws IOException {
        // 发送消息到队列
        product.send(
                "exchange_spring",
                "rowKey_like",
                "111",
                RETRY_PRE_KEY + 2
        );
    }
}
