package com.hmdp.utils;

import jakarta.annotation.Resource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RateLimitUtil {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // 限流
    private static final DefaultRedisScript<Long> FOLLOW_RATE_LIMIT_SCRIPT;

    static {
        FOLLOW_RATE_LIMIT_SCRIPT = new DefaultRedisScript<>();
        FOLLOW_RATE_LIMIT_SCRIPT.setLocation(new ClassPathResource("rateLimit.lua"));
        FOLLOW_RATE_LIMIT_SCRIPT.setResultType(Long.class);
    }

    public Long getRateLimit(String key, long limit, long timeout) {
        return stringRedisTemplate.execute(
                FOLLOW_RATE_LIMIT_SCRIPT,
                List.of(key),
                String.valueOf(limit),
                String.valueOf(timeout)
        );
    }
}
