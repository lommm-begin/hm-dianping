package com.hmdp.authority.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.authority.VerifyRule;
import com.hmdp.dto.UserDTO;
import com.hmdp.utils.JwtUtil;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static com.hmdp.utils.constants.AuthoritiesConstants.*;
import static com.hmdp.utils.constants.RedisConstants.*;

@Component
@Slf4j
public class VerifyJwtExistInRedis implements VerifyRule {
    private final String autho = "autho";
    private final String sub = "sub";
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private JwtUtil jwtUtil;

    @Resource
    private ObjectMapper objectMapper;

    @Override
    public boolean verifyRule(HttpServletRequest request, HttpServletResponse response) throws JsonProcessingException {
        return verifyRedis(request, response);
    }

    private boolean verifyRedis(HttpServletRequest request, HttpServletResponse response) throws JsonProcessingException {
        // 从请求头获取token
        String token = request.getHeader("Authorization");

        if (token == null) {
            return false;
        }

        token = token.replace("Bearer ", "");

        if (StrUtil.isBlank(token)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }

        // 验证token
        if (!jwtUtil.verifyToken(token)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }

        // 获取唯一标识jti
        String jti = jwtUtil.getJti(token);
        System.out.println(token);

        // 判断是否为空
        if (StrUtil.isBlank(jti)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }

        // 从redis获取数据
        String key = LOGIN_USER_KEY + jti;
        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(key);
        if (entries.isEmpty()) {
            return false;
        }

        // 解析权限
        Object o = entries.get(autho);
        if (o == null) {
            return false;
        }
        String autho = o.toString();

        List<String> authos = objectMapper.readValue(autho, new TypeReference<List<String>>() {});

        // 从redis查询是否存在
        Long exp = stringRedisTemplate.getExpire(key);

        // 判断是否已经登录
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            // 放入安全上下文
            if (! saveInSecurtyContext(entries, authos)) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return false;
            }
        }

        // 判断是否即将过期
        if (exp < JWT_TOKEN_EXPIRE) { // 例如，剩余小于5分钟
            // 续期
            refreshTokenAndRenewalRedis(response, entries, key, authos, jti);
        }

        return true;
    }

    private void refreshTokenAndRenewalRedis(HttpServletResponse response, Map<Object, Object> entries, String key, List<String> authos, String jti) throws JsonProcessingException {
        String token;
        long expire = JWT_TOKEN_TTL +
                ThreadLocalRandom.current().nextInt(LOGIN_USER_MIN_SEC, LOGIN_USER_MAX_SEC);
        // 生成新的token， 续期redis
        token = jwtUtil.generateAccessToken(
                String.valueOf(entries.get(sub)),
                authos,
                ISS,
                jti,
                expire * 1000);
        log.info("token: {}", token);

        // 放到响应头
        response.setHeader("authorization", token);

        // 给redis续期
        stringRedisTemplate.opsForHash().put(key, autho, objectMapper.writeValueAsString(authos));
        stringRedisTemplate.expire(key, Duration.ofSeconds(expire));
    }

    private boolean saveInSecurtyContext(Map<Object, Object> entries, List<String> authos) {
        // 将map转换成对象
        UserDTO userDTO = BeanUtil.fillBeanWithMap(entries, new UserDTO(), false);

        UsernamePasswordAuthenticationToken upat = new UsernamePasswordAuthenticationToken(userDTO,
                null,
                authos.stream().map(SimpleGrantedAuthority::new).toList());
        // 放入安全上下文
        SecurityContextHolder.getContext().setAuthentication(upat);
        return true;
    }
}
