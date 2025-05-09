package com.hmdp.utils;

public class RedisConstants {
    // 验证码前缀
    public static final String LOGIN_CODE_KEY = "login:code:";
    // 验证码有效期
    public static final Long LOGIN_CODE_TTL = 2L * 60 * 1000;
    // 验证码增加随机过期时间
    public static final long LOGIN_CODE_PER_MILLISECONDS = 60 * 1000;
    public static final long LOGIN_CODE_MIN_MILLISECONDS = 30 * 1000;

    // 用户token
    public static final String LOGIN_USER_KEY = "login:token:";
    // 用户token有效期
    public static final Long LOGIN_USER_TTL = 60L * 60 * 1000;
    public static final long LOGIN_USER_PER_MILLISECONDS = 60 * 1000;
    public static final long LOGIN_USER_MIN_MILLISECONDS = 30 * 1000;

    public static final Long CACHE_NULL_TTL = 2L;
    // 店铺过期时间
    public static final Long CACHE_SHOP_TTL = 30L * 60 * 1000;
    public static final long CACHE_SHOP_PER_MILLISECONDS = 60 * 1000;
    public static final long CACHE_SHOP_MIN_MILLISECONDS = 30 * 1000;

    // 缓存商铺数据
    public static final String CACHE_SHOP_KEY = "cache:shop:";
    public static final String CACHE_SHOP_TYPE_KEY = "cache:shop:type:";

    public static final String LOCK_SHOP_KEY = "lock:shop:";
    public static final Long LOCK_SHOP_TTL = 5L;
    public static final Long LOCK_SHOP_EXPIRE_TTL = 30L;

    public static final String ORDER_PREFIX_KEY = "order:";
    public static final String SECKILL_LOCK_KEY = "lock:seckill:";
    public static final String SECKILL_STOCK_KEY = "seckill:stock:";
    // 秒杀优惠券获取成功标识
    public static final long SUCCESS_STATUS = 0;
    // 用户的优惠券前缀
    public static final String VOUCHER_KEY = "voucher:user:";
    // 店铺的优惠券信息
    public static final String VOUCHER_SHOP_KEY = "voucher:shop:";
    public static final long SECKILL_STOCK_TTL = 1200;
    public static final long SECKILL_RETRY_TTL = 30;
    public static final long SECKILL_TTL = 5;

    // blog的首页内容
    public static final String BLOG_INDEX_KEY = "blog:index:";
    // blog 首页内容过期时间
    public static final long BLOG_INDEX_TTL = 60 * 10;
    // 记录用户给哪篇blog点赞
    public static final String BLOG_LIKED_KEY = "blog:liked:";
    // 发送消息的点赞数量前缀
    public static final String BLOG_LIKED_COUNT_KEY = "blog:liked:count:";
    // 记录需要同步的点赞用户id
    public static final String BLOG_LIKED_USER_KEY = "blog:liked:user:";
    // 记录共同关注
    public static final String FOLLOW_KEY = "follow:";
    public static final String FEED_KEY = "feed:";
    public static final String SHOP_GEO_KEY = "shop:geo:";
    public static final String USER_SIGN_KEY = "sign:";

    // 布隆过滤器标识
    public static final String BLOOM_FILTER_KEY = "bloom:filter:";
    // ack 重试次数
    public static final Integer RETRY_COUNT = 3;
    // 获取优惠券重试
    public static final String RETRY_PRE_KEY = "retry:";
    // 重试过期时间
    public static final long RETRY_TTL = 60L * 2;

    // 关注的限流的键
    public static final String RATE_KEY = "rate:";
    // 关注的限流次数
    public static final int RATE_COUNT = 1;
    // 关注的限流持续时间
    public static final long DURATION_SEC = 1L;

    // 延迟双删的延迟时间下限
    public static final long DELAY_MIN_MILLIS = 500L;

    // 延迟双删的延迟时间上限
    public static final long DELAY_MAX_MILLIS = 1000L;
}
