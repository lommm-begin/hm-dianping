package com.hmdp.mq.consume;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.mq.product.Product;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IFollowService;
import com.hmdp.service.impl.BlogServiceImpl;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.SystemConstants;
import com.rabbitmq.client.Channel;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.hmdp.utils.constants.RedisConstants.*;
import static org.springframework.amqp.support.AmqpHeaders.DELIVERY_TAG;

@Service
@Slf4j
public class BlogServiceConsumer {

    @Resource
    private IBlogService blogService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private IFollowService followService;

    @Resource
    private BlogServiceImpl blogServiceImpl;

    @Resource
    private Product product;

    @Resource
    private ObjectMapper objectMapper;

    @RabbitListener(queues = "queue_likeUpdate", ackMode = "MANUAL", concurrency = "3")
    public void likeUpdate(String blogId, Message message, Channel channel, @Header(DELIVERY_TAG) long tag) throws IOException {
        if (message == null || message.getBody() == null) {
            log.error("消息为空");
            return;
        }

        String key = BLOG_LIKED_COUNT_KEY + blogId;
        // 从redis读取当前的点赞数量
        String likedCount = stringRedisTemplate.opsForValue().get(key);

        if (likedCount == null) {
            // 跳过无效数据
            log.warn("无效数据，跳过处理");
            return;
        }

        // 读取当前点赞数
        Blog one = blogServiceImpl.query()
                .select("liked")
                .eq("id", blogId)
                .one();

        // 同步点赞数量到数据库
        boolean isSuccess = blogService.update()
                .set("liked", likedCount)
                .eq("liked", one.getLiked())
                .eq("id", blogId)
                .update();

        if (!isSuccess) {
            log.error("修改数据库点赞数量失败id: {}", blogId);
            // 重新放回list
            stringRedisTemplate.opsForSet().add(BLOG_LIKED_USER_KEY, blogId);
        }
    }

    @RabbitListener(queues = "queue_like", ackMode = "MANUAL")
    public void likeUpdateConsumer(long blogId, Message message, Channel channel, @Header(DELIVERY_TAG) long tag) {

        if (message == null || message.getBody() == null) {
            log.error("消息为空");
            return;

        }

        // 将用户id和关注者的id存入redis
        stringRedisTemplate.opsForSet()
                .add(BLOG_LIKED_USER_KEY, String.valueOf(blogId));
        // 获取博主id
        Blog one = blogServiceImpl.lambdaQuery()
                .select(Blog::getUserId)
                .eq(Blog::getId, blogId)
                .one();
        // 推送到博客博主的邮箱

    }

    @RabbitListener(queues = "queue_blogPush", ackMode = "MANUAL")
    public void feedFollow(Long id, Message message, Channel channel, @Header(DELIVERY_TAG) long tag) {
        if (message == null || message.getBody() == null) {
            log.error("消息为空");
            return;
        }

        // 查询笔记作者的所有粉丝 select * from tb_follow where follow_user_id = ?
        List<Follow> followUserId = followService.lambdaQuery()
                .eq(Follow::getFollowUserId, id)
                .list();

        // 推送给笔记id的给所有粉丝
        followUserId.forEach((follow) -> {
            // 获取粉丝id
            Long followId = follow.getId();
            // 推送
            String key = FEED_FOLLOW_NEW_BLOG_KEY + followId;
            stringRedisTemplate.opsForZSet().add(key, String.valueOf(id), System.currentTimeMillis());
        });
    }

    @RabbitListener(queues = "queue_blogHot", ackMode = "MANUAL")
    public void blogHotConsumer(Integer data, Message message, Channel channel, @Header(DELIVERY_TAG) long tag) {
        // 继续验证是否已经更新到 redis
        Object o = redisTemplate.opsForValue().get(BLOG_INDEX_TTL);
        if (o != null) {
            LocalDateTime localDateTime = (LocalDateTime) o;
            if (LocalDateTime.now().isBefore(localDateTime)) {
                return;
            }
        }

        // 根据用户查询
        Page<Blog> page = blogServiceImpl.query()
                .orderByDesc("liked")
                .page(new Page<>(data, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> blogs = page.getRecords();
        // 查询用户
        blogs.forEach(blog -> {
            blogServiceImpl.queryBlogUser(blog);
            blogServiceImpl.isBlogLiked(blog);
        });

        // 从redis获取点赞数量
        blogs.forEach(blogServiceImpl::updateLikedCount);

        Map<Long, Object> collect = blogs.stream()
                .collect(Collectors.toMap(
                        Blog::getId,
                        blog -> (Object) blog
                ));

        // 存入到redis
        redisTemplate.execute(new SessionCallback<Object>() {
            @Override
            public Object execute(RedisOperations operations) throws DataAccessException {
                try {
                    operations.multi();
                    operations.opsForHash().putAll(BLOG_INDEX_KEY, collect);
                    operations.opsForValue().set(
                            BLOG_INDEX_TTL,
                            LocalDateTime.now().plusSeconds(BLOG_INDEX_SEC_TTL));
                    return operations.exec();
                } catch (Exception e) {
                    operations.discard();
                    throw new RuntimeException(e);
                }
            }
        });
    }
}
