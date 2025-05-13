package com.hmdp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.concurrent.TimeUnit;

@Data
@AllArgsConstructor
public class ShopRedisMessage {
    String key;
    Object data;
    long time;
    TimeUnit timeUnit;
}
