package com.hmdp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hmdp.entity.UserDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserDetailMapper extends BaseMapper<UserDetail> {
    List<String> getAuthoritiesForUser(@Param("role") String role);

    List<String> getAuthoritiesByUserId(@Param("uid") Long uid);

    UserDetail getUserDetail(@Param("username") String username);
}
