package com.hmdp.aop;

import jakarta.annotation.Resource;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

@Component
@Aspect
public class LockAspect {

    @Resource
    private RedissonClient redisson;

    @Around("@annotation(com.hmdp.annotation.Lock) && args(key)")
    public void lock(ProceedingJoinPoint joinPoint, String key) throws Throwable {
        RLock lock = redisson.getLock(LOCK_SHOP_KEY + key);
        boolean locked = false;

        try {
            locked = lock.tryLock(
                    LOCK_SHOP_TTL,
                    LOCK_SHOP_EXPIRE_TTL,
                    TimeUnit.SECONDS);

            joinPoint.proceed();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        } finally {
            if (locked) {
                lock.unlock();
            }
        }
    }
}
