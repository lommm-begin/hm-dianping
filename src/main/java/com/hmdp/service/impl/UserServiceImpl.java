package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.entity.UserDetail;
import com.hmdp.entity.UserRole;
import com.hmdp.mapper.UserDetailMapper;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.mapper.RoleUserMapper;
import com.hmdp.utils.JwtUtil;
import com.hmdp.utils.RegexUtils;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;
import static com.hmdp.utils.constants.AuthoritiesConstants.*;
import static com.hmdp.utils.constants.RedisConstants.*;

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

    @Resource
    private PasswordEncoder passwordEncoder;

    @Resource
    private UserDetailMapper userDetailMapper;

    @Resource
    private AuthenticationManager authenticationManager;

    @Resource
    private RoleUserMapper roleUserMapper;

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
        stringRedisTemplate.opsForValue().set(
                LOGIN_CODE_KEY + phone,
                code,
                LOGIN_CODE_TTL +
                        ThreadLocalRandom
                                .current()
                                .nextLong(LOGIN_CODE_MIN_MILLIS, LOGIN_CODE_PER_MILLIS),
                TimeUnit.MILLISECONDS);

        // 发送验证码
        log.debug("发送验证码成功，验证码是{}", code);

        // 返回ok
        return Result.ok();
    }

    @Override
    public Result register(LoginFormDTO loginForm, HttpSession session) {
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
        User user = lambdaQuery().eq(User::getPhone, phone).oneOpt().orElse(null);

        List<String> authorities;

        // 判断用户是否存在
        if (user == null) {
            user = createUserWithPhone(phone);
            authorities = List.of(USER_INFO);
        } else {
            authorities = getAuthorities(user);
        }

        // 保存用户信息到Redis
        // 生成jwt
        String token;
        try {
            token = saveTokenToRedis(user, authorities);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        return Result.ok(token);
    }

    private List<String> getAuthorities(User user) {
        // 查询对应的权限
        return userDetailMapper.getAuthoritiesByUserId(user.getId());
    }

    public String saveTokenToRedis(User user, List<String> authorities) throws JsonProcessingException {
        String token;
        String jti = user.getId() + UUID.randomUUID().toString(true);
        long ttl = (JWT_TOKEN_TTL +
                ThreadLocalRandom.current().nextInt(LOGIN_USER_MIN_SEC, LOGIN_USER_MAX_SEC)) * 1000;
        // 生成jwt token
        token = jwtUtil.generateAccessToken(
                String.valueOf(user.getId()),
                authorities,
                ISS,
                jti,
                ttl);
        log.info("token: {}", token);

        // 将 JWT 转换为固定长度的哈希值
        String key = LOGIN_USER_KEY + jti;

        Map<String, Object> value = BeanUtil
                .beanToMap(BeanUtil.copyProperties(user, UserDTO.class));
        value.put("sub", user.getId());
        value.put("iss", ISS);
        value.put("autho", objectMapper.writeValueAsString(authorities));
        value.put("exp", ttl);

        // 保存到redis
        stringRedisTemplate.opsForHash().putAll(key, value);

        // 设置有效期
        stringRedisTemplate.expire(
                key,
                ttl,
                TimeUnit.MILLISECONDS);
        return token;
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

    @Override
    public Result login(LoginFormDTO loginForm) {
        try {
            UsernamePasswordAuthenticationToken upat =
                    new UsernamePasswordAuthenticationToken(loginForm.getPhone(), loginForm.getPassword());

            authenticationManager.authenticate(upat);

            // 查询用户信息
            User user = lambdaQuery().eq(User::getPhone, loginForm.getPhone()).one();

            // 获取对应权限
            List<String> authorities = getAuthorities(user);

            // 保存token信息到redis
            String token = saveTokenToRedis(user, authorities);
            // 放到安全上下文
            UsernamePasswordAuthenticationToken up = new UsernamePasswordAuthenticationToken(loginForm.getPhone(),
                    null,
                    authorities.stream().map(SimpleGrantedAuthority::new).toList());
            SecurityContextHolder.getContext().setAuthentication(up);

            return Result.ok(token);
        } catch (AuthenticationException e) {
            return Result.fail("手机号或密码错误");
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Result sign() {
        UserDTO userDTO = getUserDTO();
        if (userDTO == null) return Result.fail("未登录用户");
        // 指定key
        LocalDateTime now = LocalDateTime.now();
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userDTO.getId() + keySuffix;

        // 获取当天日期
        int dayOfMonth = now.getDayOfMonth();

        // 存入redis
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);

        return Result.ok();
    }

    @Override
    public Result signCount() {
        UserDTO userDTO = getUserDTO();
        if (userDTO == null) return Result.fail("未登录用户");

        // 指定key
        LocalDateTime now = LocalDateTime.now();
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userDTO.getId() + keySuffix;

        // 获取当天日期
        int dayOfMonth = now.getDayOfMonth();

        // 获取本月截止今天的所有签到次数，返回一个十进制数 bitfield key get u结束位置 开始位置
        List<Long> result = stringRedisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth - 1)).valueAt(0)
        );

        // 判断是否为空
        if (result == null || result.isEmpty()) {
            return Result.ok(0);
        }

        Long sign = result.get(0);
        if (sign == null) {
            return Result.ok(0);
        }

        int count = 0;
        // 让每一个位和1与运算，通过和最后一位比较
        // 未签到，直接结束
        while ((sign & 1) != 0) {
            // 判断是否已经签到
            count++;
            sign >>>= 1;
        }

        return Result.ok(count);
    }

    private static UserDTO getUserDTO() {
        // 获取用户id
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if (!(principal instanceof UserDTO userDTO)) {
            return null;
        }
        return userDTO;
    }

    public User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(6));
        user.setPassword(passwordEncoder.encode("123456")); // RandomUtil.randomNumbers(10)
        log.warn("{} 密码: {}", phone, user.getPassword());

        // 保存用户到数据库
        save(user);

        // 设置初始值
        UserDetail userDetail = new UserDetail();
        userDetail.setDetailId(user.getId());
        userDetail.setEnabled(true);
        userDetail.setAccountNonExpired(true);
        userDetail.setCredentialsNonExpired(true);
        userDetail.setAccountNonLocked(true);

        // 保存到数据库
        userDetailMapper.insert(userDetail);
        roleUserMapper.insert(new UserRole(user.getId(), 1L));

        return user;
    }
}