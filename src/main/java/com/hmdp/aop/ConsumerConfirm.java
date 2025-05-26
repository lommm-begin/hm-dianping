package com.hmdp.aop;

import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Aspect
@Slf4j
public class ConsumerConfirm {

    @Around("execution(* com.hmdp.mq.consume..*(..))")
    public void around(ProceedingJoinPoint joinPoint){
        Channel channel= (Channel) joinPoint.getArgs()[2];
        long tag = (long) joinPoint.getArgs()[3];
        try {
            joinPoint.proceed();

            channel.basicAck(tag, false); // 不批量确认
            log.info("消息手动确认成功, tag={}, channelOpen={}", tag, channel.isOpen());
        }   catch (Throwable e) {
            log.error("处理消息时运行时异常");
            log.error(e.getMessage());
            try {
                channel.basicNack(tag, false, true);
            } catch (IOException ex) {
                log.error(ex.getMessage());
            }
        }
    }
}
