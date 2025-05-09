package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.poi.excel.sax.ElementName;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.mq.product.Product;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RateLimitUtil;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import jakarta.annotation.Resource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static cn.hutool.poi.excel.sax.ElementName.v;
import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Resource
    private IUserService userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private Product product;

    @Resource
    private RateLimitUtil rateLimitUtil;

    @Override
    public Result queryBlogHot(Integer current) {

        Object o = redisTemplate.opsForValue().get(BLOG_INDEX_KEY);
        List<Blog> range = null;

        if (o instanceof List<?> value) {
            if (value.get(0) instanceof Blog) {
                range = (List<Blog>) value;
            }
        }

        // 存在，则直接返回
        if (range != null) {
            return Result.ok(range);
        }

        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            this.queryBlogUser(blog);
            this.isBlogLiked(blog);
        });

        // 从redis获取点赞数量
        records = records.stream()
                .peek(this::updateLikedCount).toList();

        // 存入到redis
        redisTemplate.opsForValue().set(BLOG_INDEX_KEY, records, Duration.ofSeconds(BLOG_INDEX_TTL));

        return Result.ok(records);
    }

    // 从redis同步点赞数量
    private void updateLikedCount(Blog blog) {
        String key = BLOG_LIKED_COUNT_KEY + blog.getId();
        try {
            // 查询点赞数量
            String liked = stringRedisTemplate.opsForValue().get(key);
            if (liked != null) {
                // 替换点赞数量
                blog.setLiked(Integer.parseInt(liked));
            } else {
                // 不存在redis，则存储到 redis
                stringRedisTemplate.opsForValue().set(key, String.valueOf(blog.getLiked()));
            }
        } catch (NumberFormatException e) {
            log.error("同步redis点赞数量时，发送错误，数据回滚: {}", e);
        }
    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

    @Override
    public Result queryBlogById(Long id) {
        // 查询blog
        Blog blog = getById(id);

        if (blog == null) {
            return Result.fail("笔记不存在");
        }
        // 查询blog用户
        queryBlogUser(blog);

        // 判断是否点赞过这篇blog
        isBlogLiked(blog);

        // 点赞数量
        updateLikedCount(blog);

        return Result.ok(blog);
    }

    private void isBlogLiked(Blog blog) {
        // 获取登录用户
        Long id = UserHolder.getUser().getId();
        if (id == null) {
            return;
        }

        // 判断当前用户是否已经点赞
        Double isMember = stringRedisTemplate.opsForZSet()
                .score(BLOG_LIKED_KEY + blog.getId(), id.toString());

        // true证明已经点过赞
        blog.setIsLike(isMember == null ? Boolean.FALSE : Boolean.TRUE);
    }

    // 立马更新到redis，暂存到消息队列，在消息队列接收时根据计时5秒同步redis，修改到数据库
    @Override
    public Result likeBlog(Long id) {

        if (rateLimitUtil.getRateLimit(RATE_KEY + id, RATE_COUNT, DURATION_SEC) == 0) {
            return Result.fail("请勿频繁操作！");
        }

        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }

        String key = BLOG_LIKED_KEY + id;
        String countKey = BLOG_LIKED_COUNT_KEY + id;

        // 获取登录用户
        Long userId = user.getId();
        if (userId == null) {
            return Result.fail("请先登录");
    }

        try {
            // 判断当前用户是否已经点赞
            Double isMember = stringRedisTemplate.opsForZSet()
                    .score(key, userId.toString());

            // 保存到redis
            boolean isSuccess = (boolean) stringRedisTemplate.execute(new SessionCallback<Object>() {
                @Override
                public Object execute(RedisOperations operations) throws DataAccessException {

                    String v = userId.toString();

                    try {
                        operations.multi();

                        // 如果未点赞，则允许点赞
                        if (isMember == null) {
                            // 保存用户到redis的set集合
                            operations.opsForZSet()
                                    .add(key, v, System.currentTimeMillis());

                            // 点赞数 + 1
                            operations.opsForValue().increment(countKey);
                        } else {
                            // 把用户从redis的set集合移除
                            operations.opsForZSet()
                                    .remove(key, v);
                            // 点赞数 - 1
                            operations.opsForValue().decrement(countKey);
                        }
                        return operations.exec() != null ? true : false;
                    } catch (Exception e) {
                        operations.discard();
                        log.error("执行redis命令发送错误: " + e.getMessage());
                        return false;
                    }
                }
            });

            if (isSuccess) {
                // 发送消息到队列
                product.send(
                        "exchange_spring",
                        "rowKey_like",
                        id,
                        RETRY_PRE_KEY + countKey
                );
            }
        } catch (Exception e) {
            log.error("处理点赞的消息发送到交换机时发生错误: {}", e);
        }

        return Result.ok();
    }

    @Override
    public Result queryBlogLikes(Long id) {
        // 查询TOP5的点赞用户 zrange key 0 4
        Set<String> topId = stringRedisTemplate.opsForZSet().range(BLOG_LIKED_KEY + id, 0, 4);

        // 没有用户点赞
        if (topId == null || topId.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        // 解析出其中的用户id
        List<Long> list = topId.stream().map(Long::valueOf).toList();

        String join = StrUtil.join(",", list);

        // 根据用户id查询用户
        List<UserDTO> userDTOList = userService.query()
                .in("id", list).last("order by field(id, " + join + ")").list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .toList();
        return Result.ok(userDTOList);
    }

    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        Long id = user.getId();
        blog.setUserId(id);

        // 保存探店博文
        boolean save = save(blog);

        // 判断是否保存成功
        if (save) {
            // 发送到队列
            product.sendDelayedTask(
                    "exchange_spring",
                    "rowKey_blogPush",
                    id,
                    RETRY_PRE_KEY + id,
                    0L
            );
        }

        return Result.ok(blog.getId());
    }
}