package com.hmdp.intercept;

import com.hmdp.authority.VerifyRule;
import com.hmdp.utils.RoutePolicy;
import jakarta.annotation.Resource;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.pattern.PathPatternParser;

import java.io.IOException;
import java.util.Map;

@Component
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    @Resource
    private RoutePolicy routePolicy;

    @Resource
    private Map<String, VerifyRule> rulesMap;

    @Resource
    private PathPatternParser pathPatternParser;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        // 验证路径
        String ruleType = routePolicy.decideStrategy(request);

        if (ruleType == null) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        String requestURI = request.getRequestURI();
        if ("/user/login".equals(requestURI)
                || "/user/register".equals(requestURI)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (pathPatternParser.parse("/blog/{id}").matches(PathContainer.parsePath(requestURI))
        && request.getHeader("Authorization") != null) {
            ruleType = "verifyJwtExistInRedis";
        }

        // 根据bean名称选择不同的验证策略
        if (!rulesMap.get(ruleType).verifyRule(request, response)) {
            return;
        }

        filterChain.doFilter(request, response);
    }
}
