package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RateLimitUtil;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
@RequestMapping("/voucher-order")
public class VoucherOrderController {

    @Resource
    private IVoucherOrderService voucherOrderServiceImplByRabbitmq;
    @Resource
    private RateLimitUtil rateLimitUtil;

    @PostMapping("/seckill/{id}")
    public Result seckillVoucher(@PathVariable("id") Long voucherId) {
        if (rateLimitUtil.getRateLimit(RATE_KEY + voucherId, RATE_COUNT, DURATION_SEC) == 0) {
            return Result.fail("请勿频繁操作！");
        }
        return voucherOrderServiceImplByRabbitmq.seckillVoucher(voucherId);
    }
}
