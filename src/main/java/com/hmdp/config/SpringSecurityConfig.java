package com.hmdp.config;

import com.hmdp.intercept.JwtAuthenticationFilter;
import com.hmdp.utils.JwtNonCheckPath;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SpringSecurityConfig {
    @Resource
    private JwtAuthenticationFilter jwtAuthenticationFilter;
    @Resource
    private JwtNonCheckPath jwtNonCheckPath;
    @Resource
    private UserDetailsService userDetailsService;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(authorizeRequests -> {
                    authorizeRequests
                            .requestMatchers(
                                    jwtNonCheckPath.getStrategies().get(0)
                                            .getPaths().toArray(String[]::new)
                            ).permitAll()
                            .anyRequest().authenticated();
                })
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .csrf(AbstractHttpConfigurer::disable)
                .rememberMe(rememberMe -> {
                    rememberMe.key("remember-me")
                            .tokenValiditySeconds(3600)
                            .userDetailsService(userDetailsService);
                })
                .build();
    }
}

