package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IFollowService;
import com.hmdp.utils.RateLimitUtil;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static com.hmdp.utils.constants.RedisConstants.*;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/follow")
public class FollowController {
    @Resource
    private IFollowService followService;
    @Resource
    private RateLimitUtil rateLimitUtil;

    @PreAuthorize("hasAuthority('user:follow')")
    @PutMapping("/{id}/{isFollow}")
    public Result follow(@PathVariable long id, @PathVariable boolean isFollow) {
        if (rateLimitUtil.getRateLimit(RATE_KEY, RATE_COUNT, DURATION_SEC) == 0) {
            return Result.fail("请勿频繁操作！");
        }
        return followService.follow(id, isFollow);
    }

    @GetMapping("/or/not/{id}")
    public Result follow(@PathVariable long id) {
        return followService.isFollow(id);
    }

    /**
     * 共同关注
     * @param id
     * @return
     */
    @GetMapping("/common/{id}")
    public Result commonFollow(@PathVariable long id) {
        return followService.commonFollow(id);
    }
}
