package com.hmdp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.*;

@Configuration
public class SpringConfig {

    @Bean
    public ExecutorService executorService() {
        return new ThreadPoolExecutor(
                10,  // 核心线程数
                20, // 最大线程数
                10,  // 临时线程空闲存活时间
                TimeUnit.SECONDS, // 时间单位
                new LinkedBlockingQueue<>(100), // 任务队列
                Executors.defaultThreadFactory(), // 线程工厂
                new ThreadPoolExecutor.AbortPolicy() // 拒绝策略（这里选择AbortPolicy）
        );
    }
}
