package com.hmdp.intercept;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.log4j.Log4j2;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

@Log4j2
public class LoggerForRequestPathIntercept implements HandlerInterceptor {
    private static final ThreadLocal<Long> startTime = new ThreadLocal<>();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        try {
            startTime.set(System.currentTimeMillis());
            log.info(request.getRequestURI());
        } catch (Exception e) {

        }
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        try {
            Long endTime = startTime.get();
            if (endTime == null) {
                return;
            }
            log.info("{} 响应时间 {}", request.getRequestURI(), System.currentTimeMillis() - endTime);
            startTime.remove();
        } catch (Exception e) {

        }
    }
}
