package com.hmdp.utils;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "security.jwt")
@Component
@Data
public class JwtNonCheckPath {
    private List<Strategy> strategies;

    @Data
    public static class Strategy {
        private List<String> paths = new ArrayList<>();
        private String strategy;
    }
}
