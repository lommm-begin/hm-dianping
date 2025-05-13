package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.UserDetailDTO;
import com.hmdp.entity.User;
import com.hmdp.entity.UserDetail;
import com.hmdp.mapper.UserDetailMapper;
import com.hmdp.mapper.UserMapper;
import jakarta.annotation.Resource;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CustomUserDetailsServiceImpl implements UserDetailsService {
    @Resource
    private UserMapper userMapper;
    @Resource
    private UserDetailMapper userDetailMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // 到数据库查询
        User user = userMapper.selectOne(
                new QueryWrapper<User>()
                        .eq("phone", username));
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在！");
        }

        // 查询用户认证信息到UserDTO
        UserDetail userDetail = userDetailMapper.getUserDetail(username);

        List<String> authoritiesByUserId = userDetailMapper.getAuthoritiesByUserId(user.getId());
        userDetail.setAuthorities(authoritiesByUserId);

        // 存入信息, 返回给spring
        return new UserDetailDTO(userDetail);
    }
}
