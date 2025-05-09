package com.hmdp.mq.consume;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.entity.Follow;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IFollowService;
import com.hmdp.utils.RedisConstants;
import com.rabbitmq.client.Channel;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

import static com.hmdp.utils.RedisConstants.*;
import static org.springframework.amqp.support.AmqpHeaders.DELIVERY_TAG;

@Service
@Slf4j
public class BlogServiceConsumer {
    private boolean stop = false;

    @Resource
    private IBlogService blogService;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IFollowService followService;

    // 参数是必须返回了再开始计时下一次的定时任务
    @Scheduled(fixedDelay = 3000)
    public void updateLikes() {
        try {
            for (Long size = stringRedisTemplate.opsForSet().size(BLOG_LIKED_USER_KEY);
                 !stop && size != null && size > 0;) {

                String blogId = stringRedisTemplate.opsForSet().pop(BLOG_LIKED_USER_KEY);
                if (blogId == null) {
                    log.warn("blogId is null");
                    break;
                }

                try {
                    String key = BLOG_LIKED_COUNT_KEY + blogId;
                    // 从redis读取当前的点赞数量
                    String likedCount = stringRedisTemplate.opsForValue().get(key);

                    if (likedCount == null) {
                        // 跳过无效数据
                        log.warn("无效数据，跳过处理");
                        continue;
                    }

                    // 同步点赞数量到数据库
                    boolean isSuccess = blogService.update()
                            .set("liked", Integer.parseInt(likedCount))
                            .eq("id", Long.parseLong(blogId))
                            .update();

                    if (!isSuccess) {
                        log.error("修改数据库点赞数量失败id: {}", blogId);
                        // 重新放回list
                        stringRedisTemplate.opsForSet().add(BLOG_LIKED_USER_KEY, blogId);
                    }
                } catch (NumberFormatException e) {
                    log.error("数据格式错误: {}", e.getMessage());
                } catch (Exception e) {
                    log.error("修改数据库点赞数时发生异常id: {}", blogId, e);
                }
            }
        } catch (Exception e) {
            log.error("同步数据库点赞数量发生异常，已捕获处理: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void setStop() {
        stop = true;
    }

    @RabbitListener(queues = "queue_like", ackMode = "MANUAL")
    public void likeUpdateConsumer(Message message, Channel channel, @Header(DELIVERY_TAG) long tag) {

        if (message == null || message.getBody() == null) {
            log.error("消息为空");
            return;
        }
        try {
            // 解析blogId
            long blogId = objectMapper.readValue(message.getBody(), Long.class);

            // 将用户id和关注者的id存入redis
            stringRedisTemplate.opsForSet()
                    .add(BLOG_LIKED_USER_KEY, String.valueOf(blogId));

            channel.basicAck(tag, false); // 不批量确认
        } catch (JsonProcessingException e) {
            log.error("处理点赞信息反序列化发送异常");
        } catch (IOException e) {
            log.error("处理点赞消息时运行时异常");
            try {
                channel.basicNack(message.getMessageProperties().getDeliveryTag(), false, true);
            } catch (IOException ex) {
                log.error(ex.getMessage());
            }
        }
    }

    @RabbitListener(queues = "queue_blogPush", ackMode = "MANUAL")
    public void feedFollow(Message message, Channel channel, @Header(DELIVERY_TAG) long tag) {
        if (message == null || message.getBody() == null) {
            log.error("消息为空");
            return;
        }

        try {
            // 获取博主id
            long id = Long.parseLong(new String(message.getBody()));

            // 查询笔记作者的所有粉丝 select * from tb_follow where follow_user_id = ?
            List<Follow> followUserId = followService.query()
                    .eq("follow_user_id", id)
                    .list();

            // 推送给笔记id的给所有粉丝
            followUserId.forEach((follow) -> {
                // 获取粉丝id
                Long followId = follow.getId();
                // 推送
                String key = FEED_KEY + followId;
                stringRedisTemplate.opsForZSet().add(key, String.valueOf(id), System.currentTimeMillis());
            });
            channel.basicAck(tag, false); // 不批量确认
        } catch (JsonProcessingException e) {
            log.error("处理推送信息反序列化发送异常");
        } catch (IOException e) {
            log.error("处理推送消息时运行时异常");
            try {
                channel.basicNack(message.getMessageProperties().getDeliveryTag(), false, true);
            } catch (IOException ex) {
                log.error(ex.getMessage());
            }
        }
    }
}
