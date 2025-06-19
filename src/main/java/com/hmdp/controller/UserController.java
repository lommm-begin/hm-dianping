package com.hmdp.controller;


import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.UserInfo;
import com.hmdp.service.IUserInfoService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RateLimitUtil;
import com.hmdp.utils.UserHolder;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import static com.hmdp.utils.constants.RedisConstants.*;


/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {
    @Resource
    private RateLimitUtil rateLimitUtil;

    @Resource
    private IUserService userService;

    @Resource
    private IUserInfoService userInfoService;

    /**
     * 发送手机验证码
     */
    @PostMapping("/code")
    public Result sendCode(@RequestParam("phone") String phone, HttpSession session) {
        if (rateLimitUtil.getRateLimit(RATE_KEY + "code:" + phone, RATE_COUNT, DURATION_SEC) == 0) {
            return Result.fail("请勿频繁操作！");
        }
        // 发送短信验证码并保存验证码
        return userService.sendCode(phone, session);
    }

    /**
     * 注册功能
     *
     * @param loginForm 登录参数，包含手机号、验证码；或者手机号、密码
     */
    @PostMapping("/register")
    public Result register(@RequestBody LoginFormDTO loginForm, HttpSession session) {
        if (rateLimitUtil.getRateLimit(RATE_KEY + loginForm.getPhone(), RATE_COUNT, DURATION_SEC) == 0) {
            return Result.fail("请勿频繁操作！");
        }
        // 实现注册功能
        return userService.register(loginForm, session);
    }

    /**
     * 登出功能
     *
     * @return 无
     */
    @PostMapping("/logout")
    public Result logout(HttpServletRequest request, HttpServletResponse response) {
        UserDTO principal = (UserDTO) (SecurityContextHolder.getContext().getAuthentication().getPrincipal());
        if (rateLimitUtil.getRateLimit(RATE_KEY +  principal.getId(), RATE_COUNT, DURATION_SEC) == 0) {
            return Result.fail("请勿频繁操作！");
        }
        return userService.logout(request, response);
    }

    @GetMapping("/me")
    public Result me() {
        //  获取当前登录的用户并返回
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return Result.ok();
        }
        // 获取登录用户
        Object principal = authentication.getPrincipal();
        if (principal == null) {
            return Result.ok();
        }
        if (!(principal instanceof UserDTO userDTO)) {
            return Result.ok();
        }

        return Result.ok(userDTO);
    }

    @PreAuthorize("hasAuthority('user:info')")
    @GetMapping("/info/{id}")
    public Result info(@PathVariable("id") Long userId) {
        // 查询详情
        UserInfo info = userInfoService.getById(userId);
        if (info == null) {
            // 没有详情，应该是第一次查看详情
            return Result.ok();
        }
        info.setCreateTime(null);
        info.setUpdateTime(null);
        // 返回
        return Result.ok(info);
    }

    @GetMapping("/{id}")
    public Result queryUserById(@PathVariable("id") Long userId) {
        return userService.queryUserById(userId);
    }

    /**
     * 登录功能
     *
     * @param loginForm 登录参数，包含手机号、验证码；或者手机号、密码
     */
    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO loginForm) {

        // 实现登录功能
        return userService.login(loginForm);
    }

    /**
     * 重置密码
     * @param loginForm
     * @return
     */
    @PostMapping("/reset")
    public Result resetUser(@RequestBody LoginFormDTO loginForm) {
        return userService.resetUser(loginForm);
    }

    /**
     * 签到
     * @return
     */
    @PostMapping("/sign")
    public Result sign() {
        return userService.sign();
    }

    @GetMapping
    public Result signCountForMouth() {
        return userService.signCount();
    }
}