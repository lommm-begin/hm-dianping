package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;
import java.util.concurrent.TimeUnit;

// 使用redis实现的分布式锁
public class SimpleRedisLock implements ILock{
    // 锁的名称
    private static final String KEY_PREFIX = "lock:";
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-"; // 去除连字符
    private StringRedisTemplate stringRedisTemplate;
    private String name;

    public SimpleRedisLock(StringRedisTemplate stringRedisTemplate, String name) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.name = name;
    }

    @Override
    public boolean tryLock(long timeoutSeconds) {

        // setnx 操作
        return Boolean.TRUE.equals(stringRedisTemplate.opsForValue() // 防止空指针
                .setIfAbsent(KEY_PREFIX + name,
                        ID_PREFIX + Thread.currentThread().getId(), // Thread.currentThread().threadId(), java19+
                        timeoutSeconds, TimeUnit.SECONDS));
    }

    // 基于lua脚本
    @Override
    public void unlock() {
        String UNLOCK_SCRIPT =
                """
                    -- 比较线程中和锁中的标识是否一致
                    if (redis.call('get', KEYS[1]) == ARGV[1]) then
                        -- 释放锁
                        return redis.call('del', KEYS[1])
                    end
                    return 0
                        """;
        stringRedisTemplate.execute(
                new DefaultRedisScript<>(UNLOCK_SCRIPT, Long.class),
                List.of(KEY_PREFIX + name),
                ID_PREFIX + Thread.currentThread().getId());
    }

    //    @Override
//    public void unlock() {
//        String redisThreadId = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
//        String lockThreadId = ID_PREFIX + Thread.currentThread().getId();
//
//        if ((lockThreadId).equals(redisThreadId)) {
//            stringRedisTemplate.delete(KEY_PREFIX + name);
//        }
//    }
}
