package com.hmdp.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitMQConfig {

    @Bean
    public MessageConverter messageConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        converter.setCreateMessageIds(true);
        return converter;
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter());
        return rabbitTemplate;
    }

    /**
     * 第一个参数：队列的名称
     * 第二个参数：是否持久化
     * 第三个参数：是否私有
     * 第四个参数：在没有消费者订阅的情况下是否删除
     * 第五个参数：结构化的数据，比如死信队列
     */
    @Bean
    public Queue queue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", "dlx.myDLX"); // 死信交换机
        args.put("x-dead-letter-routing-key", "rowkey_DLR"); // 死信路由键
        args.put("x-message-ttl", 5000); // 消息存活时间 (ms)
        return new Queue("queue_spring", true, false, false, args); // 队列名称、是否持久化、是否排他、是否自动删除、参数
    }


    /**
     * 这里指定的是类型交换机，根基路由键绑定的队列进行发送
     * 第一个参数：交换机的名称
     * 第二个参数：是否持久化
     * 第三个参数：是否在五队列取消绑定后删除
     * 第四个参数：结构化的数据，比如死信队列
     * @return
     */
    @Bean
    public DirectExchange exchange() {
        return new DirectExchange("exchange_spring", true, false, null);
    }

    @Bean
    public Binding binding(Queue queue, DirectExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with("rowKey_spring");
    }

    @Bean
    public Queue dlxQueue() {
        return new Queue("dlx.myDLQ", true, false, false, null);
    }

    @Bean
    public DirectExchange dlxExchange() {
        return new DirectExchange("dlx.myDLX", true, false, null);
    }

    @Bean
    public Binding dlxBinding(Queue dlxQueue, DirectExchange dlxExchange) {
        return BindingBuilder.bind(dlxQueue).to(dlxExchange).with("rowkey_DLR");
    }

    // 处理点赞信息的队列
    @Bean
    public Queue likesQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", "dlx.myDLX"); // 死信交换机
        args.put("x-dead-letter-routing-key", "rowkey_DLR"); // 死信路由键
        args.put("x-message-ttl", 5000); // 消息存活时间 (ms)
        return new Queue("queue_like", true, false, false, args); // 队列名称、是否持久化、是否排他、是否自动删除、参数
    }

    @Bean
    public Binding bindingLikes(Queue likesQueue, DirectExchange exchange) {
        return BindingBuilder.bind(likesQueue).to(exchange).with("rowKey_like");
    }

    // 更新点赞数量的队列
    @Bean
    public Queue likesUpdateQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", "dlx.myDLX"); // 死信交换机
        args.put("x-dead-letter-routing-key", "rowkey_DLR"); // 死信路由键
        args.put("x-message-ttl", 5000); // 消息存活时间 (ms)
        return new Queue("queue_likeUpdate", true, false, false, args); // 队列名称、是否持久化、是否排他、是否自动删除、参数
    }

    @Bean
    public Binding bindingLikeUpdate(Queue likesUpdateQueue, DirectExchange exchange) {
        return BindingBuilder.bind(likesUpdateQueue).to(exchange).with("rowKey_likeUpdate");
    }

    // 处理博主发布新内容推送的队列
    @Bean
    public Queue blogPushQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", "dlx.myDLX"); // 死信交换机
        args.put("x-dead-letter-routing-key", "rowkey_DLR"); // 死信路由键
        args.put("x-message-ttl", 5000); // 消息存活时间 (ms)
        return new Queue("queue_blogPush", true, false, false, args); // 队列名称、是否持久化、是否排他、是否自动删除、参数
    }

    @Bean
    public Binding bindingBlogPush(Queue blogPushQueue, DirectExchange exchange) {
        return BindingBuilder.bind(blogPushQueue).to(exchange).with("rowKey_blogPush");
    }

    @Bean
    public CustomExchange delayedExchange() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-delayed-type", "direct"); // 延迟交换机的类型
        return new CustomExchange("delayed_exchange", "x-delayed-message", true, false, args);
    }

    // 用来实现延时双删的队列
    @Bean
    public Queue delayQueue() {
        return new Queue("queue_delay", true, false, false); // 队列名称、是否持久化、是否排他、是否自动删除、参数
    }

    @Bean
    public Binding bindingDelay(Queue delayQueue, CustomExchange delayedExchange) {
        return BindingBuilder.bind(delayQueue).to(delayedExchange).with("rowKey_delay").noargs();
    }

    // 店铺信息
    @Bean
    public Queue ShopMessageQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", "dlx.myDLX"); // 死信交换机
        args.put("x-dead-letter-routing-key", "rowkey_DLR"); // 死信路由键
        args.put("x-message-ttl", 5000); // 消息存活时间 (ms)
        return new Queue("queue_shopMessage", true, false, false, args); // 队列名称、是否持久化、是否排他、是否自动删除、参数
    }

    @Bean
    public Binding ShopMessageBind(Queue ShopMessageQueue, DirectExchange exchange) {
        return BindingBuilder.bind(ShopMessageQueue).to(exchange).with("rowKey_shopMessage");
    }

    // 首页热点blog
    @Bean
    public Queue BlogHotQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", "dlx.myDLX"); // 死信交换机
        args.put("x-dead-letter-routing-key", "rowkey_DLR"); // 死信路由键
        args.put("x-message-ttl", 5000); // 消息存活时间 (ms)
        return new Queue("queue_blogHot", true, false, false, args); // 队列名称、是否持久化、是否排他、是否自动删除、参数
    }

    @Bean
    public Binding bindingBlogHot(Queue BlogHotQueue, DirectExchange exchange) {
        return BindingBuilder.bind(BlogHotQueue).to(exchange).with("rowKey_blogHot");
    }
}

