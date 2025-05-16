package com.hmdp.intercept;

import com.hmdp.authority.VerifyRule;
import com.hmdp.utils.RoutePolicy;
import jakarta.annotation.Resource;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

@Component
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    @Resource
    private RoutePolicy routePolicy;

    @Resource
    private Map<String, VerifyRule> rulesMap;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        // 验证路径
        String ruleType = routePolicy.decideStrategy(request);

        if (!rulesMap.get(ruleType).verifyRule(request, response)) {
            return;
        }
        filterChain.doFilter(request, response);

    }
}
