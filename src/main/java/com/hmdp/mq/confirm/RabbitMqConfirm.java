package com.hmdp.mq.confirm;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mq.product.Product;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

import static com.hmdp.utils.constants.RedisConstants.*;

@Component
@Slf4j
public class RabbitMqConfirm {
    @Resource
    private RabbitTemplate rabbitTemplate;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private Product product;

    @PostConstruct
    public void init() {
        log.debug("RabbitMQProduct生产者确认机制初始化成功");
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            if (correlationData == null) {
                return;
            }
            String retryKey = correlationData.getId();
            // 判断是否发送到路由器
            if (ack) {
                log.info("生产者确认消息成功-{}", retryKey);
                // 确认成功，删除计数器
                stringRedisTemplate.delete(retryKey);
                return;
            }

            String key = retryKey.replace(RETRY_PRE_KEY, "");

            // 调用优惠券重试机制
            if (key.startsWith(VOUCHER_KEY)) {
                log.warn("开始重试");
                handleVoucherRetry(correlationData, retryKey, key);
                return;
            }
            // 点赞博客id重试机制
            if (key.startsWith(BLOG_LIKED_COUNT_KEY)) {
                handleAddLikedBlogIdRetry(correlationData, retryKey);
                return;
            }
            // 更新店铺信息重试机制
            if (key.startsWith(CACHE_SHOP_KEY)) {
                handleShopUpdateRetry(correlationData, retryKey);
            }
            // 更新点赞数量
            if (key.startsWith(BLOG_LIKED_COUNT_KEY)) {
                handleBlogLikedUpdateRetry(correlationData, retryKey, key);
            }
        });

        // 发送到交换机，路由失败
        rabbitTemplate.setReturnsCallback(returned -> {
            String correlationId = returned.getMessage().getMessageProperties().getCorrelationId();
            log.error("到达交换机, 但消息[{}]路由失败！交换机:{} 路由键:{} 错误原因:{}",
                    correlationId,
                    returned.getExchange(),
                    returned.getRoutingKey(),
                    returned.getReplyText());

            if (correlationId.startsWith(RETRY_PRE_KEY + VOUCHER_KEY)) {
                try {
                    // 发送到死信队列
                    rabbitTemplate.convertAndSend(
                            "dlx.myDLX",
                            "dlx.myDLQ",
                            returned.getMessage().getBody(),
                            message -> {
                                message.getMessageProperties().setCorrelationId(correlationId);
                                return message;
                            });
                    log.info("成功发送到优惠券死信队列");
                } catch (AmqpException e) {
                    log.error("发送到优惠券死信队列时发生错误");
                }
            }
        });
    }

    private void handleBlogLikedUpdateRetry(CorrelationData correlationData, String retryKey, String key) {
        String blogId = key.replace(BLOG_LIKED_COUNT_KEY, "");
        if (retry(correlationData, key)) {
            // 重新放回list
            stringRedisTemplate.opsForSet().add(BLOG_LIKED_USER_KEY, blogId);
            return;
        }
        // 从redis获取数据
        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(retryKey);

        // 重发
        retrySend(key, entries, blogId, correlationData);
    }

    private void handleShopUpdateRetry(CorrelationData correlationData, String retryKey) {
    }

    private void handleAddLikedBlogIdRetry(CorrelationData correlationData, String retryKey) {

    }

    private void handleVoucherRetry(CorrelationData correlationData, String retryKey, String key) {
        if (retry(correlationData, key)) return;

        // 从redis获取数据
        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(key);
        VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(entries, new VoucherOrder(), true);
        voucherOrder.setId(Long.parseLong(entries.get("orderId").toString()));

        // 重发
        retrySend(key, entries, voucherOrder, correlationData);
    }

    private boolean retry(CorrelationData correlationData, String key) {
        String RETRY_SCRIPT =
                """
                        local key = KEYS[1]
                        local retryCount = redis.call('HGET', key, 'retryCount')
                        
                        -- 判断是否存在
                        if not retryCount then
                            return 404
                        end
                        
                        -- 判断是否已达上限
                        if tonumber(retryCount) > tonumber(ARGV[1]) then
                            -- 重试次数达到上限
                            redis.call('DEL', key)
                            return 0
                        end
                        
                        -- 次数递增1
                        redis.call('HINCRBY', key, 'retryCount', 1)
                        -- 未到达上限
                        return 1
                        """;
        Long executed = stringRedisTemplate.execute(
                new DefaultRedisScript<>(RETRY_SCRIPT, Long.class),
                Collections.singletonList(key),
                String.valueOf(RETRY_COUNT)
        );

        if (executed.intValue() == 0) {
            log.warn("重试次数达到上限");
            // 发送到死信队列
            sendToDLQ(correlationData, key);

            return true;
        }
        return false;
    }

    private void retrySend(String key, Map<Object, Object> entries, Object data, CorrelationData correlationData) {
        product.send(
                entries.get("exchange").toString(),
                entries.get("rowKey").toString(),
                Objects.requireNonNull(correlationData.getReturned()).getMessage().getMessageProperties().getHeader("x-message-type"),
                data,
                key
        );
    }

    private void sendToDLQ(CorrelationData correlationData, String key) {
        try {
            // 发送到死信队列
            rabbitTemplate.convertAndSend(
                    "dlx.myDLX",
                    "dlx.myDLQ",
                    BeanUtil.fillBeanWithMap(stringRedisTemplate.opsForHash().entries(key), new VoucherOrder(), true),
                    message -> {
                        message.getMessageProperties().setCorrelationId(correlationData.getId());
                        return message;
                    });
            log.info("发送到交换机失败，成功发送到优惠券死信队列");
        } catch (AmqpException e) {
            log.error("发送到交换机失败，发送到优惠券死信队列时发生错误");
        }
    }
}