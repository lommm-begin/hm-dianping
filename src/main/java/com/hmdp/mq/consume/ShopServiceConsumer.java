package com.hmdp.mq.consume;

import com.rabbitmq.client.Channel;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;

import static org.springframework.amqp.support.AmqpHeaders.DELIVERY_TAG;

@Service
@Slf4j
public class ShopServiceConsumer {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @RabbitListener(queues = "queue_delay", ackMode = "MANUAL")
    public void delayDeleteInRedis(Message message, Channel channel, @Header(DELIVERY_TAG) long tag) {
        String key = new String(message.getBody());
        System.out.println(LocalDateTime.now());

        log.info("手动确认消息: {}", key);
        try {
            channel.basicAck(tag, false); // 不批量确认

            stringRedisTemplate.delete(key);
        } catch (IOException e) {
            log.error("手动确认消息时发生错误, 错误{}", e.getMessage());
            try {
                // 不批量拒绝确认，重新放回队列
                channel.basicNack(tag, false, true);
            } catch (IOException ex) {
                log.error("拒绝时发生错误");
            }
        }
    }
}
