package com.hmdp;

import com.hmdp.entity.UserDetail;
import com.hmdp.mapper.UserDetailMapper;
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
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.constants.RedisConstants.*;

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

    @Resource
    UserDetailMapper userDetailMapper;

    @Test
    public void contextLoads() {

    }

    @Test
    public void testShopService() {
        for (int i = 1; i <= 14; i++) {
        }
    }

    // 缓存店铺信息
    @Test
    public void testShopService2() {
        cacheClient.setWithLogicalExpire(
                CACHE_SHOP_KEY + 1,
                shopService.getById(1),
                5,
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
                .putAll(SECKILL_STOCK_KEY + 1,
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
                1000L * 5
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
                "test",
                "111",
                RETRY_PRE_KEY + 2
        );

        System.in.read();
    }

//    @Test
//    public void testShopService7() throws IOException {
//        UserDetail userDetailByUsername = userDetailMapper.getUserDetailByUsername("18029624303");
//        List<Integer> ids = Arrays.stream(userDetailByUsername.getAuthorities().split(","))
//                .map(Integer::parseInt)
//                .toList();
//        List<String> authoritiesByAuthoId = userDetailMapper.getAuthoritiesByAuthoId(ids);
//
//        System.out.println(userDetailByUsername);
//        authoritiesByAuthoId.forEach(System.out::println);
//    }

    @Test
    public void testShopService8() throws IOException {
        System.out.println(userDetailMapper.getUserDetail("18029624303"));
        System.out.println(userDetailMapper.getAuthoritiesByUserId(1020L));
        System.out.println(userDetailMapper.getAuthoritiesForUser("user"));
    }

    @Test
    public void testShopService7() throws IOException {
        // 查询用户认证信息到UserDTO
        UserDetail userDetail = userDetailMapper.getUserDetail("18029624303");
        System.out.println(userDetail.getAuthorities());
    }
}
