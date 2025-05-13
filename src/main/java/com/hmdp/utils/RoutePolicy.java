package com.hmdp.utils;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.util.Collection;
import java.util.function.Supplier;

@Component
public class RoutePolicy {
    @Resource
    private JwtNonCheckPath jwtNonCheckPath;
    @Resource
    private AntPathMatcher antPathMatcher;

    public AuthStrategy decideStrategy(HttpServletRequest request) {
        // 无需登录
        if (matchPath(jwtNonCheckPath::getSkipPath, request)) {
            return AuthStrategy.SKIP; // 提前返回
        }
        if (matchPath(jwtNonCheckPath::getSingleVerify, request)) {
            return AuthStrategy.JWT_TOKEN;
        }
        if (matchPath(jwtNonCheckPath::getDoubleVerify, request)) {
            return AuthStrategy.DOUBLE_VERIFY;
        }

        return null;
    }

    private <T extends Collection<String>> boolean matchPath(Supplier<T> function, HttpServletRequest request) {
        return function.get().stream()
                .anyMatch(path -> antPathMatcher.match(path, request.getRequestURI()));
    }
}
