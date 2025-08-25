package com.hmdp;

import com.hmdp.service.impl.UserServiceImpl;
import com.hmdp.utils.JwtUtil;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import static com.hmdp.utils.constants.RedisConstants.USER_SIGN_KEY;

@SpringBootTest
public class UserServiceImpiTest {
    @Resource
    UserServiceImpl userService;

    @Resource
    JwtUtil jwtUtil;

    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Test
    public void test01() {
        userService.createUserWithPhone("18029624304");
    }

    @Test
    public void test02() {
        Boolean b = jwtUtil.verifyToken("eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpYXQiOjE3NDY4ODY1NzYsImV4cCI6MTc0Njg4ODg0MCwic3ViIjoiMTAyMCIsIkF1dGhvcml6YXRpb25zIjpbInVzZXI6aW5mbyJdLCJqdGkiOiIxMDIwOGIyZjY4NTM3OGRiNGQzMThjZGNmZTdlMmNiMWM5ZGEiLCJpc3MiOiJodHRwOi8vbG9jYWxob3N0OjgwODEifQ.82XnXKyTANF-ijAzdnG5YV744EMHaKl8hC6FC7lqV0g");
        if (b) {
            System.out.println("通过");
        } else {
            System.out.println("不通过");
        }
    }

    @Test
    public void test03() {
        String key = USER_SIGN_KEY + "1020" + ":202506";
        // 存入redis
        stringRedisTemplate.opsForValue().setBit(key, 17, true);
    }

    @Test
    public void test04() {

    }
}
