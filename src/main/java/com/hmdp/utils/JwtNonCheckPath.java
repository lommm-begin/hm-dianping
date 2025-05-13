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
    private List<String> skipPath = new ArrayList<>();
    private List<String> singleVerify = new ArrayList<>();
    private List<String> doubleVerify = new ArrayList<>();
}
