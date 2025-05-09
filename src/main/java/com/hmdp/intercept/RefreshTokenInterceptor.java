package com.hmdp.intercept;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.utils.UserHolder;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;

@Slf4j
@Component
public class RefreshTokenInterceptor implements HandlerInterceptor {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 获取session中的用户
//        Object userDTO = request.getSession().getAttribute(USER);

        // 从请求头获取token
        String token = request.getHeader("authorization");

        // 判断是否为空
        if (StrUtil.isBlank(token)) {
            // 提前结束
            return true;
        }

        //  打印token
        log.warn(token);

        String key = LOGIN_USER_KEY + DigestUtil.sha256Hex(token);

        Map<Object, Object> entries = stringRedisTemplate
                .opsForHash().entries(key);

        // 判断用户是否存在
        if (entries == null) {
            // 提前结束
            return true;
        }

        // 将map转换成对象
        UserDTO userDTO = BeanUtil.fillBeanWithMap(entries, new UserDTO(), false);

        // 存在，保存在ThreadLocal
        UserHolder.saveUser(userDTO);

        // token 续期
        stringRedisTemplate.expire(key, LOGIN_USER_TTL, TimeUnit.MINUTES);

        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        UserHolder.removeUser();
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        HandlerInterceptor.super.afterCompletion(request, response, handler, ex);
    }
}
