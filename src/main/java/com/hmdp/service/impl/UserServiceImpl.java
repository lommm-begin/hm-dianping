package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.JwtUtil;
import com.hmdp.utils.RegexUtils;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
@Transactional(rollbackFor = Exception.class)
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private JwtUtil jwtUtil;

    @Resource
    private ObjectMapper objectMapper;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 不符合，返回错误信息
            return Result.fail("手机号格式错误");
        }

        // 符合，生成验证码
        String code = RandomUtil.randomNumbers(6);

        // 保存验证码
//        session.setAttribute(phone, code);

        stringRedisTemplate.opsForValue().set(
                LOGIN_CODE_KEY + phone,
                code,
                LOGIN_CODE_TTL +
                        ThreadLocalRandom
                                .current()
                                .nextLong(LOGIN_CODE_MIN_MILLISECONDS, LOGIN_CODE_PER_MILLISECONDS),
                TimeUnit.MILLISECONDS);

        // 发送验证码
        log.debug("发送验证码成功，验证码是{}", code);

        // 返回ok
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 校验手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 不符合，返回错误信息
            return Result.fail("手机号格式错误");
        }

        // 校验验证码
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();

        // 不一致，返回错误
        // 判断生成的验证码是否为空的原因是，防止用户直接点击登录，而非先生成验证码
        if (cacheCode == null || !cacheCode.equals(code)) {
            return Result.fail("验证码错误");
        }

        // 一致，根据手机号查询用户 select * from tb_user where phone
        User user = query().eq("phone", phone).one();

        // 判断用户是否存在
        if (user == null) {
            user = createUserWithPhone(phone);
        }

        // 保存用户信息到Redis
        // 生成jwt
        String token = null;
        try {
            token = jwtUtil.generateAccessToken(objectMapper.writeValueAsString(user), null);
            log.info("token: {}", token);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        // 将 JWT 转换为固定长度的哈希值
        String key = LOGIN_USER_KEY + DigestUtil.sha256Hex(token);

        Map<String, Object> value = BeanUtil
                .beanToMap(BeanUtil.copyProperties(user, UserDTO.class));

        // 保存到redis
        stringRedisTemplate.opsForHash().putAll(key, value);

        // 设置有效期
        stringRedisTemplate.expire(
                key,
                LOGIN_USER_TTL +
                ThreadLocalRandom
                        .current()
                        .nextLong(LOGIN_USER_MIN_MILLISECONDS, LOGIN_USER_PER_MILLISECONDS),
                TimeUnit.MILLISECONDS);

        return Result.ok(token);
    }

    @Override
    public Result queryUserById(Long userId) {
        // 查询详情
        User user = getById(userId);
        if (user == null) {
            return Result.ok();
        }
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        // 返回
        return Result.ok(userDTO);
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(6));

        // 保存用户到数据库
        save(user);

        return user;
    }
}
