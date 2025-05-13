package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.dto.ShopRedisMessage;
import com.hmdp.mq.product.Product;
import jakarta.annotation.Resource;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.hmdp.utils.constants.RedisConstants.*;

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
    private Product product;

    public void setWithLogicalExpire(String key, Object value, long time, TimeUnit timeUnit) {
        try {
            RedisData redisData = new RedisData();
            redisData.setData(value);
            redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(time)));
            stringRedisTemplate.opsForValue().set(
                    key,
                    objectMapper.writeValueAsString(redisData));
        } catch (JsonProcessingException e) {
            log.error("序列化失败: {}", e.getMessage());
        }
    }

    public <R, ID> R queryByIdFromRedis(
            String keyPre,
            ID id,
            Class<R> clazz,
            String retryKey,
            Long time,
            Long preTime,
            Long minTime,
            TimeUnit timeUnit) {
        String key = keyPre + id;

        // 从 redis 查询
        String redisData = stringRedisTemplate.opsForValue().get(key);

        RedisData bean = null;
        R r = null;
        // 判断是否存在
        if (StrUtil.isNotBlank(redisData)) {
            bean = JSONUtil.toBean(redisData, RedisData.class);
            JSONObject entries = (JSONObject) bean.getData();
            r = JSONUtil.toBean(entries, clazz);
        }

        // 不存在或者逻辑时间过期
        if (BeanUtil.isEmpty(bean) || bean.getExpireTime().isBefore(LocalDateTime.now())) {
            RLock lock = redissonClient.getLock(LOCK_SHOP_KEY + key);
            boolean islock = false;
            try {
                islock = lock.tryLock(30, TimeUnit.SECONDS);
                // 获取锁成功
                if (!islock) {
                   return r;
                }
                // 双重验证是否已经更新到 redis
                String string = stringRedisTemplate.opsForValue().get(key);
                if (!(StrUtil.isBlank(string)
                        || JSONUtil.toBean(string, RedisData.class)
                        .getExpireTime().isBefore(LocalDateTime.now()))) {
                    return r;
                }

                // 发送到消息队列
                product.send(
                        "exchange_spring",
                        "rowKey_shopMessage",
                        "shopMessage",
                        new ShopRedisMessage(key, id, time + ThreadLocalRandom.current().nextLong(minTime, preTime), timeUnit),
                        retryKey
                );
            } catch (InterruptedException e) {
                log.error(e.getMessage());
            } finally {
                if (islock && lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        }

        // 所有都直接返回旧数据
        return r;
    }
}
