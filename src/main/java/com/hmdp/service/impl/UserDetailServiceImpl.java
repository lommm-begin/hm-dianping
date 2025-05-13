package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.entity.UserDetail;
import com.hmdp.mapper.UserDetailMapper;
import com.hmdp.service.IUserDetailService;
import org.springframework.stereotype.Service;

@Service
public class UserDetailServiceImpl extends ServiceImpl<UserDetailMapper, UserDetail> implements IUserDetailService {

}
