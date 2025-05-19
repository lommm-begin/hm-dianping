package com.hmdp.mq.consume;

import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.IVoucherOrderService;
import com.rabbitmq.client.Channel;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

import java.io.IOException;

import static org.springframework.amqp.support.AmqpHeaders.DELIVERY_TAG;

@Service
@Slf4j
public class VoucherOrderServiceConsumer {
    @Resource
    private IVoucherOrderService voucherOrderService;

    @RabbitListener(queues = "queue_spring", ackMode = "MANUAL")
    public void handleVoucherOrder(VoucherOrder voucherOrder, Message message, Channel channel, @Header(DELIVERY_TAG) long tag) throws IOException {
        if (message == null || message.getBody() == null) {
            log.error("消息为空");
            return;
        }

        handleAckMessage(message, channel, voucherOrder);
    }

    private void handleAckMessage(Message message, Channel channel, VoucherOrder voucherOrder) throws IOException {
        voucherOrderService.createVoucherOrder(voucherOrder);
    }
}
