package com.hmdp.mq.product;

import cn.hutool.core.lang.UUID;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class Product {

    @Resource
    private RabbitTemplate rabbitTemplate;

    public void send(
            @NotNull @NotBlank String exchange,
            @NotNull @NotBlank String routingKey,
            @NotNull @NotBlank String businessType,
            @NotNull Object data,
            @NotNull @NotBlank String retryKey) {

        CorrelationData correlationData = new CorrelationData(retryKey);

        // 发送消息
        rabbitTemplate.convertAndSend(exchange, routingKey, data, message -> {
            message.getMessageProperties().setCorrelationId(retryKey);
            message.getMessageProperties().setHeader("x-message-type", businessType);
            return message; // 设置唯一id
        }, correlationData);
    }

    // 延迟队列
    public void sendDelayedTask(
            @NotNull @NotBlank String exchange,
            @NotNull @NotBlank String routingKey,
            @NotNull Object data,
            @NotNull @NotBlank String key,
            long delayMillis) {

        CorrelationData correlationData = new CorrelationData(key);

        // 发送消息
        rabbitTemplate.convertAndSend(exchange, routingKey, data, message -> {
            message.getMessageProperties().setCorrelationId(key);
            message.getMessageProperties().setHeader("x-delay", delayMillis);
            message.getMessageProperties().setDelayLong(delayMillis);
            return message; // 设置唯一id
        }, correlationData);
    }
}