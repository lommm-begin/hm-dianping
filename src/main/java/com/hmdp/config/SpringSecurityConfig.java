package com.hmdp.config;

import com.hmdp.intercept.JwtAuthenticationFilter;
import com.hmdp.utils.JwtNonCheckPath;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SpringSecurityConfig {
    @Resource
    private JwtAuthenticationFilter jwtAuthenticationFilter;
    @Resource
    private JwtNonCheckPath jwtNonCheckPath;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(authorizeRequests -> {
                    authorizeRequests
                            .requestMatchers(
                                    jwtNonCheckPath.getSkipPath().toArray(String[]::new)
                            ).permitAll()
                            .anyRequest().authenticated();
                })
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .csrf(AbstractHttpConfigurer::disable)
                .build();
    }
}

