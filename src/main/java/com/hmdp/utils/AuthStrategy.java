package com.hmdp.utils;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 验证策略
 */
@Component
@Data
@ConfigurationProperties("rule.type")
public class AuthStrategy {
    private String skip;
    private String jwtToken;
    private String jwtAndRedis;
}
