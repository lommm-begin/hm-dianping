package com.hmdp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hmdp.entity.UserRole;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RoleUserMapper extends BaseMapper<UserRole> {

}
