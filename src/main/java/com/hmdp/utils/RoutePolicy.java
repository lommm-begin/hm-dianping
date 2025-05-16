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

    @Resource
    AuthStrategy authStrategy;

    public String decideStrategy(HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        // 无需登录
        if (matchPath(jwtNonCheckPath::getSkipPath, requestURI)) {
            return authStrategy.getSkip(); // 提前返回
        }
        if (matchPath(jwtNonCheckPath::getSingleVerify, requestURI)) {
            return authStrategy.getJwtToken();
        }
        if (matchPath(jwtNonCheckPath::getDoubleVerify, requestURI)) {
            return authStrategy.getJwtAndRedis();
        }

        return null;
    }

    private <T extends Collection<String>> boolean matchPath(Supplier<T> function, String requestURI) {
        return function.get().stream()
                .anyMatch(path -> antPathMatcher.match(path, requestURI));
    }
}
