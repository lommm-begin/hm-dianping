package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("tb_user_detail")
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
public class UserDetail {
    @TableId
    private Long detailId;

    private String username; // 用户名

    private String password; // 密码

    private boolean enabled; // 账户是否启用

    @TableField("accountNonExpired")
    private boolean accountNonExpired; // 账户是否未过期

    @TableField("credentialsNonExpired")
    private boolean credentialsNonExpired; // 凭证是否未过期

    @TableField("accountNonLocked")
    private boolean accountNonLocked; // 账户是否未锁定

    private List<String> authorities;
}
