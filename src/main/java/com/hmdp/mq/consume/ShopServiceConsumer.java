package com.hmdp.mq.consume;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.ShopRedisMessage;
import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import com.rabbitmq.client.Channel;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import static com.hmdp.utils.constants.RedisConstants.LOCK_SHOP_KEY;
import static com.hmdp.utils.constants.RedisConstants.RETRY_PRE_KEY;
import static org.springframework.amqp.support.AmqpHeaders.DELIVERY_TAG;

@Service
@Slf4j
public class ShopServiceConsumer {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private ShopServiceImpl shopService;

    @RabbitListener(queues = "queue_delay", ackMode = "MANUAL")
    public void delayDeleteInRedis(String key, Message message, Channel channel, @Header(DELIVERY_TAG) long tag) {

        log.info("手动确认消息: {}", key);
        stringRedisTemplate.delete(key);
    }

    @RabbitListener(queues = "queue_shopMessage")
    public void shopMessage(ShopRedisMessage shopRedisMessage, Message message, Channel channel, @Header(DELIVERY_TAG) long tag) throws JsonProcessingException {
        if (shopRedisMessage == null) {
            return;
        }

        // 继续验证是否已经更新到 redis
        String string = stringRedisTemplate.opsForValue()
                .get(message.getMessageProperties().getCorrelationId().replace(RETRY_PRE_KEY, ""));
        if (!(StrUtil.isBlank(string)
                || JSONUtil.toBean(string, RedisData.class)
                .getExpireTime().isBefore(LocalDateTime.now()))) {
            return;
        }

        // 到数据库查询数据
        Shop result = shopService.query()
                .lambda()
                .eq(Shop::getId, shopRedisMessage.getData())
                .one();
        RedisData redisData = new RedisData();
        redisData.setData(result);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(
                shopRedisMessage.getTimeUnit().toSeconds(shopRedisMessage.getTime())
        ));

        // 存入redis
        String key = shopRedisMessage.getKey();
        stringRedisTemplate.opsForValue().set(
                key,
                objectMapper.writeValueAsString(redisData)
        );
    }
}
