package com.hmdp.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class UserDTO {

    private Long id;
    private String nickName;
    private String icon;
}
