package com.hmdp.utils;

public enum VoucherStatusType {
    NOT_VOUCHED("库存不足"), // 库存不足
    REPEAT_PURCHASE("请勿重复购买"), // 请勿重复购买
    TAKEN_OFF_THE_SHELVES("优惠券已下架"), // 下架
    UNAVAILABLE("优惠券已过期"), // 已过期
    ACTIVITY_NOT_STARTED("活动未开始"), // 活动未开始
    ACTIVITY_ALREADY_END("活动已结束"), // 活动已结束
    SYSTEM_BUSY("系统繁忙");// 系统繁忙

    private String status;

    private VoucherStatusType(String status) {
        this.status = status;
    }

    public static String voucherStatusType(int r) {
            return switch (r) {
                case 1 -> NOT_VOUCHED.status;
                case 2 -> REPEAT_PURCHASE.status;
                case 3 -> TAKEN_OFF_THE_SHELVES.status;
                case 4 -> UNAVAILABLE.status;
                case 5 -> ACTIVITY_NOT_STARTED.status;
                case 6 ->ACTIVITY_ALREADY_END.status;
                default -> SYSTEM_BUSY.status;
            };
    }
}
