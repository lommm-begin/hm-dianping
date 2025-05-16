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


    public String decideStrategy(HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        // 无需登录
        return matchPath(jwtNonCheckPath::getStrategies, requestURI); // 提前返回
    }

    private <T extends Collection<JwtNonCheckPath.Strategy>> String matchPath(Supplier<T> function, String requestURI) {
        return function.get().stream()
                .filter(strategy -> strategy.getPaths()
                    .stream()
                    .anyMatch(path -> antPathMatcher.match(path, requestURI)))
                .findFirst()
                .map(JwtNonCheckPath.Strategy::getStrategy)
                .orElse(null);
    }
}
