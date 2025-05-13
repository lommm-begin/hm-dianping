package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.mq.product.Product;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import jakarta.annotation.Resource;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

import static com.hmdp.utils.constants.RedisConstants.*;

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
    private RedissonClient redissonClient;

    @Override
    public Result queryBlogHot(Integer current) {
        if (current == null || current <= 0) {
            return Result.ok();
        }
        // 从redis获取元素
        List<Object> values = redisTemplate.opsForHash().values(BLOG_INDEX_KEY);
        boolean empty = values.stream()
                .skip(Math.min(values.size(), (current - 1) * SystemConstants.MAX_PAGE_SIZE))
                .findAny()
                .isEmpty();
        // 判断后面是否还有内容1 0 = 0,
        if (empty) {
            return Result.ok();
        }
        handleHotBlog(current);
        return Result.ok(values);
    }

    private void handleHotBlog(Integer current) {
        // 判断逻辑时间是否过期
        if (isTimeout()) {
            RLock lock = redissonClient.getLock(BLOG_INDEX_KEY + BLOG_HOT_LOCK);
            boolean isLock = false;
            try {
                // 尝试获取锁
                isLock = lock.tryLock();
                if (isLock) {
                    // 再次检查
                    if (!isTimeout()) {
                        return;
                    }
                    String retryKey = RETRY_PRE_KEY + BLOG_INDEX_KEY;
                    // 发送到队列
                    product.send(
                            "exchange_spring",
                            "rowKey_blogHot",
                            "refreshBlogHot",
                            current,
                            retryKey
                    );
                }
            } catch (Exception e) {
                log.error("更新热点数据时发生错误: {}" + e.getMessage());
            } finally {
                if (isLock && lock.isHeldByCurrentThread()) {
                    // 释放锁
                    lock.unlock();
                }
            }
        }
    }

    private boolean isTimeout() {
        Object o = redisTemplate.opsForValue().get(BLOG_INDEX_TTL);
        if (o == null) {
            return true;
        }
        // 继续验证是否已经更新到 redis
        LocalDateTime localDateTime = (LocalDateTime) o;
        return !LocalDateTime.now().isBefore(localDateTime);
    }

    // 从redis同步点赞数量
    public void updateLikedCount(Blog blog) {
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

    public void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

    @Override
    public Result queryBlogById(Long id) {
        // 查询blog
        Blog blog = (Blog) redisTemplate.opsForHash().get(BLOG_INDEX_KEY, id);

        if (blog == null) {
            return Result.fail("笔记不存在");
        }

        // 判断是否点赞过这篇blog
        isBlogLiked(blog);

        // 点赞数量
        updateLikedCount(blog);

        return Result.ok(blog);
    }

    public void isBlogLiked(Blog blog) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return;
        }
        // 获取登录用户
        Object principal = authentication.getPrincipal();
        if (principal == null) {
            return;
        }
        if (!(principal instanceof UserDTO userDTO)) {
            return;
        }

        Long id = userDTO.getId();

        // 判断当前用户是否已经点赞
        Double isMember = stringRedisTemplate.opsForZSet()
                .score(BLOG_LIKED_KEY + blog.getId(), id.toString());

        // true证明已经点过赞
        blog.setIsLike(isMember == null ? Boolean.FALSE : Boolean.TRUE);
    }

    // 立马更新到redis，暂存到消息队列，在消息队列接收时根据计时5秒同步redis，修改到数据库
    @Override
    public Result likeBlog(Long id) {

        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal == null) {
            return Result.ok();
        }
        if (!(principal instanceof UserDTO userDTO)) {
            return Result.ok();
        }

        String key = BLOG_LIKED_KEY + id;
        String countKey = BLOG_LIKED_COUNT_KEY + id;

        // 获取登录用户
        Long userId = userDTO.getId();

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
                        "likeBlog",
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
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal == null) {
            return Result.ok();
        }
        if (!(principal instanceof UserDTO userDTO)) {
            return Result.ok();
        }
        Long id = userDTO.getId();
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

    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        // 获取登录用户
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal == null) {
            return Result.ok();
        }
        if (!(principal instanceof UserDTO userDTO)) {
            return Result.ok();
        }

        // 查询收件箱 ZrevrangeByScore key Max Min Limit offset count
        String key = FEED_FOLLOW_NEW_BLOG_KEY + userDTO.getId();
        // 按分数倒序查询
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, max, offset, OFFSET_COUNT);

        // 判断是否为空
        if (typedTuples == null || typedTuples.isEmpty()) {
            return Result.ok();
        }

        // 解析数据
        List<Long> list = new ArrayList<>(typedTuples.size());
        long minTime = 0L;
        int offsetCount = 1;
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            //  获取id
            list.add(Long.valueOf(Objects.requireNonNull(typedTuple.getValue())));
            long time = Objects.requireNonNull(typedTuple.getScore()).longValue();

            // 相同，则递增
            if (minTime == time) {
                offsetCount++;
            } else {
                // 有更小的值，重置计数
                minTime = time;
                offsetCount = 1;
            }
        }

        // 根据用户id查询blog
        List<Blog> blogs = query()
                .in("id", list)
                .last("order by field(id, " + StrUtil.join(",", list) + ")")
                .list();

        blogs.forEach(blog -> {
            this.queryBlogUser(blog);
            this.isBlogLiked(blog);
        });
        // 封装数据
        ScrollResult r = new ScrollResult();
        r.setList(blogs);
        r.setOffset(offsetCount);
        r.setMinTime(minTime);

        // 返回
        return Result.ok(r);
    }
}