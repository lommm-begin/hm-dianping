package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IFollowService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

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

    @PutMapping("/{id}/{isFollow}")
    public Result follow(@PathVariable long id, @PathVariable boolean isFollow) {
        return followService.follow(id, isFollow);
    }

    @GetMapping("/or/not/{id}")
    public Result follow(@PathVariable long id) {
        return followService.isFollow(id);
    }

    @GetMapping("/common/{id}")
    public Result commonFollow(@PathVariable long id) {
        return followService.commonFollow(id);
    }
}
