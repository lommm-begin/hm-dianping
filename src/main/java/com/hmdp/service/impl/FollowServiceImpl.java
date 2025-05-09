package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RateLimitUtil;
import com.hmdp.utils.UserHolder;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

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
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserService userService;

    @Resource
    private RateLimitUtil rateLimitUtil;

    @Override
    public Result isFollow(long id) {
        // 已登录状态无需校验UserHolder.getUser()是否为空

        // 查询是否关注 select * from tb_follow where user_id = ? and followUserId = ?
        Long count = query().eq("user_id", UserHolder.getUser().getId()).eq("follow_user_id", id).count();

        return Result.ok(count > 0);
    }

    // lua脚本限流
    @Override
    public Result follow(long id, boolean isFollow) {

        if (rateLimitUtil.getRateLimit(RATE_KEY, RATE_COUNT, DURATION_SEC) == 0) {
            return Result.fail("请勿频繁操作！");
        }

        // 获取登录的用户
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("未登录");
        }
        Long userId = user.getId();
        String key = FOLLOW_KEY + userId;

        // 判断是关注还是取关
        if (isFollow) {
            // 关注新增数据
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(id);
            boolean isSuccess = save(follow);

            if (isSuccess) {
                // 把关注用户的id，放入redis的set集合 sadd userId id
                stringRedisTemplate.opsForSet().add(key,  String.valueOf(id));
            }
        } else {
            // 取关，删除数据 delete from tb_follow where userId = ? and followUserId = ?
            boolean isSuccess  = remove(new QueryWrapper<Follow>().eq("user_id", userId).eq("follow_user_id", id));

            if (isSuccess) {
                // 从redis集合中移除
                stringRedisTemplate.opsForSet().remove(key, String.valueOf(id));
            }
        }

        return Result.ok();
    }

    @Override
    public Result commonFollow(long id) {
        // 获取当前用户
        Long userId = UserHolder.getUser().getId();
        String key1 = FOLLOW_KEY + userId;
        // 求交集
        String key2 = FOLLOW_KEY + id;
        Set<String> intersect =
                stringRedisTemplate.opsForSet().intersect(key1, key2);

        if (intersect == null || intersect.isEmpty()) {
            return Result.ok();
        }

        // 转换数据
        List<Long> list = intersect.stream().map(Long::valueOf).toList();

        // 查询用户
        List<UserDTO> userDTOList = userService.listByIds(list).stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .toList();

        return Result.ok(userDTOList);
    }
}
