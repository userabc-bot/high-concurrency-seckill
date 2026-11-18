package com.seckill.common;

public final class RedisKeys {

    private RedisKeys() {
    }

    public static String stock(Long voucherId) {
        return "seckill:stock:" + voucherId;
    }

    public static String users(Long voucherId) {
        return "seckill:users:" + voucherId;
    }

    public static String result(Long voucherId, Long userId) {
        return "seckill:result:" + voucherId + ":" + userId;
    }

    public static String resultPattern(Long voucherId) {
        return "seckill:result:" + voucherId + ":*";
    }

    public static String rateLimit(Long userId) {
        return "seckill:rate:" + userId;
    }
}
