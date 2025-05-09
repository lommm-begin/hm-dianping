package com.hmdp.mq.consume;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.IVoucherOrderService;
import com.rabbitmq.client.Channel;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

import java.io.IOException;

import static com.hmdp.utils.RedisConstants.SECKILL_LOCK_KEY;
import static org.springframework.amqp.support.AmqpHeaders.DELIVERY_TAG;

@Service
@Slf4j
public class VoucherOrderServiceConsumer {
    @Resource
    private RedissonClient redissonClient;
    @Resource
    private IVoucherOrderService voucherOrderService;
    @Resource
    private ObjectMapper objectMapper;

    @RabbitListener(queues = "queue_spring", ackMode = "MANUAL")
    public void handleVoucherOrder(Message message, Channel channel, @Header(DELIVERY_TAG) long tag) {
        if (message == null || message.getBody() == null) {
            log.error("消息为空");
            return;
        }
        RLock rLock = null;
        try {
            VoucherOrder voucherOrder =
                    objectMapper.readValue(new String(message.getBody()), VoucherOrder.class);
            // 生成锁
            rLock = redissonClient.getLock(SECKILL_LOCK_KEY + voucherOrder.getUserId());
            boolean isLock = rLock.tryLock();
            if (!isLock) {
                // 获取锁失败
                log.error("消息重新入队");
                // 重新放回队列
                channel.basicReject(tag, true);
            }
            handleAckMessage(message, channel, voucherOrder);
        } catch (Exception e) {
            log.error("手动确认消息时发生错误, 错误{}", e.getMessage());
            try {
                // 不批量拒绝确认，重新放回队列
                channel.basicNack(message.getMessageProperties().getDeliveryTag(), false, true);
            } catch (IOException ex) {
                log.error("拒绝时发生错误");
            }
        } finally {
            if (rLock != null && rLock.isHeldByCurrentThread()) {
                rLock.unlock();
            }
        }
    }

    private void handleAckMessage(Message message, Channel channel, VoucherOrder voucherOrder) {
        try {
            log.info("手动确认消息: {}", voucherOrder);
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false); // 不批量确认
            voucherOrderService.createVoucherOrder(voucherOrder);
        } catch (IOException e) {
            log.error("手动确认消息发送错误");
        }
    }
}
