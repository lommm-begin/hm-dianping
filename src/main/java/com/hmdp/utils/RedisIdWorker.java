package com.hmdp.utils;

import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

// 生成随机ID
@Component
public class RedisIdWorker {

    private static final long BEGIN_TIMESTAMP = 1577836800L;
    private static final long BITS = 32L;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public long nextId(String keyPre) {
        LocalDateTime now = LocalDateTime.now();

        long epochSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = epochSecond - BEGIN_TIMESTAMP;
        // 获取当天日期
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));

        Long increment = stringRedisTemplate.opsForValue().increment("icr:" + keyPre + ":" + date);

        // 时间戳 + 计数器
        return timestamp << BITS | increment;
    }

    // 不成功的订单减一
    public void decrementId(long key) {
        stringRedisTemplate.opsForValue().decrement(key + "");
    }

    public static void main(String[] args) {
        LocalDateTime localDateTime = LocalDateTime.of(2020, 1, 1, 0, 0);
        // 将 LocalDateTime 转换为秒级时间戳
        long epochSecond = localDateTime.toEpochSecond(ZoneOffset.UTC);
        System.out.println(epochSecond);
    }
}
