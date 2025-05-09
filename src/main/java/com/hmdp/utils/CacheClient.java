package com.hmdp.utils;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

@Slf4j
@Component
public class CacheClient {

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private ExecutorService executorService;

    public void set(String key, Object value, long time, TimeUnit timeUnit) {
        try {
            stringRedisTemplate.opsForValue().set(
                    key,
                    objectMapper.writeValueAsString(value),
                    time,
                    timeUnit);
        } catch (JsonProcessingException e) {
            log.error(e.getMessage() + "序列化失败");
        }
    }

    public void setWithLogicalExpire(String key, Object value, long time, TimeUnit timeUnit) {
        try {
            RedisData redisData = new RedisData();
            redisData.setData(value);
            redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(time)));
            stringRedisTemplate.opsForValue().set(
                    key,
                    objectMapper.writeValueAsString(redisData));
        } catch (JsonProcessingException e) {
            log.error(e.getMessage() + "序列化失败");
        }
    }

    public <R, ID> R queryShopByIdFromRedis(
            String keyPre,
            ID id,
            Class<R> clazz,
            Function<ID, R> function,
            Long time,
            Long preTime,
            Long minTime,
            TimeUnit timeUnit) {
        String key = keyPre + id;

        // 从 redis 查询
        String redisData = stringRedisTemplate.opsForValue().get(key);

        // 判断是否存在，需要数据提前预热
        if (StrUtil.isBlank(redisData)) {
            // 存在，直接返回
            return null;
        }

        RedisData bean = JSONUtil.toBean(redisData, RedisData.class);
        JSONObject entries = (JSONObject) bean.getData();
        R r = JSONUtil.toBean(entries, clazz);

        // 逻辑时间过期
        if (bean.getExpireTime().isBefore(LocalDateTime.now())) {
            RLock lock = redissonClient.getLock(LOCK_SHOP_KEY + key);
            boolean locked = false;

            try {
                locked = lock.tryLock(LOCK_SHOP_TTL, LOCK_SHOP_EXPIRE_TTL, TimeUnit.SECONDS);
                // 获取锁成功
                if (locked) {
                    // 双重验证是否已经更新到 redis
                    String string = stringRedisTemplate.opsForValue().get(key);
                    if (JSONUtil.toBean(string, RedisData.class).getExpireTime().isBefore(LocalDateTime.now())) {
                        this.queryShopByIdFromDB(key, id, function, time, preTime, minTime, timeUnit);
                    }
                }
            } catch (InterruptedException e) {
                log.error(e.getMessage());
            } finally {
                if (locked) {
                    lock.unlock();
                }
            }
        }

        // 所有都直接返回旧数据
        return r;
    }

    private <R, ID> void queryShopByIdFromDB(
            String key,
            ID id,
            Function<ID, R> function,
            Long time,
            Long preTime,
            Long minTime,
            TimeUnit timeUnit) {

            CompletableFuture<Void> objectCompletableFuture = CompletableFuture.runAsync(() -> {
                // 逻辑时间过期，根据id查询数据库
                R newR = function.apply(id);

                // 不存在数据库，返回错误
                if (newR == null) {
                    // 触发错误回调
                    throw new RuntimeException();
                }

                // 存在，缓存到 redis
                this.setWithLogicalExpire(key, newR, time +
                        ThreadLocalRandom.current().nextLong(minTime, preTime), timeUnit);
            }, executorService);

            objectCompletableFuture.exceptionally(ex->{
                throw new RuntimeException("商铺不存在！！");
            });
    }
}
