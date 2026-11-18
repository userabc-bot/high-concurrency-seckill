package com.seckill.service;

import com.seckill.common.RedisKeys;
import com.seckill.common.Result;
import com.seckill.config.RabbitConfig;
import com.seckill.dto.SeckillMessage;
import com.seckill.dto.SeckillResultVO;
import com.seckill.entity.SeckillOrder;
import com.seckill.entity.SeckillVoucher;
import com.seckill.mapper.SeckillVoucherMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillService {

    private static final int RATE_LIMIT_COUNT = 5;
    private static final long RATE_LIMIT_WINDOW_MS = 1000L;
    private static final long RESULT_TTL_HOURS = 24L;
    private static final String SUCCESS_PREFIX = "SUCCESS:";
    private static final String FAILED_PREFIX = "FAILED:";

    private final StringRedisTemplate redisTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final VoucherLocalCache localCache;
    private final OrderService orderService;
    private final SeckillVoucherMapper voucherMapper;
    private final DefaultRedisScript<Long> seckillScript;
    private final DefaultRedisScript<Long> rateLimitScript;
    private final DefaultRedisScript<Long> restoreStockScript;

    public Result<?> seckill(Long userId, Long voucherId) {
        VoucherLocalCache.LocalVoucher voucher = localCache.get(voucherId);
        if (voucher == null) {
            return Result.fail(404, "秒杀活动不存在");
        }
        long now = System.currentTimeMillis();
        if (now < voucher.getBeginMs()) {
            return Result.fail(403, "活动尚未开始");
        }
        if (now > voucher.getEndMs()) {
            return Result.fail(410, "活动已结束");
        }
        if (voucher.isSoldOut()) {
            return Result.fail(410, "商品已售罄");
        }

        Long allowed = redisTemplate.execute(rateLimitScript,
                Collections.singletonList(RedisKeys.rateLimit(userId)),
                String.valueOf(RATE_LIMIT_COUNT), String.valueOf(RATE_LIMIT_WINDOW_MS));
        if (allowed == null || allowed == 0L) {
            return Result.fail(429, "请求过于频繁，请稍后再试");
        }

        Long result = redisTemplate.execute(seckillScript,
                Arrays.asList(RedisKeys.stock(voucherId), RedisKeys.users(voucherId)),
                String.valueOf(userId), String.valueOf(ttlSeconds(voucher)));
        if (result == null) {
            return Result.fail(503, "系统繁忙，请稍后再试");
        }

        if (result == 1L) {
            SeckillMessage message = new SeckillMessage();
            message.setUserId(userId);
            message.setVoucherId(voucherId);
            message.setAmount(voucher.getPrice());
            try {
                rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.ORDER_ROUTING_KEY, message);
            } catch (AmqpException e) {
                log.error("发送秒杀消息失败 voucherId={}, userId={}", voucherId, userId, e);
                restoreStock(voucherId, userId);
                return Result.fail(503, "系统繁忙，请稍后再试");
            }
            return Result.ok("抢购成功，订单处理中");
        }
        if (result == -2L) {
            return Result.fail(409, "您已抢购过该商品");
        }
        if (result == -1L) {
            localCache.markSoldOut(voucherId);
            return Result.fail(410, "商品已售罄");
        }
        return Result.fail(410, "活动已结束");
    }

    public SeckillResultVO getResult(Long userId, Long voucherId) {
        String resultKey = RedisKeys.result(voucherId, userId);
        String cached = redisTemplate.opsForValue().get(resultKey);
        if (cached != null) {
            if (cached.startsWith(SUCCESS_PREFIX)) {
                return SeckillResultVO.of(SeckillResultVO.SUCCESS, "抢购成功", cached.substring(SUCCESS_PREFIX.length()));
            }
            if (cached.startsWith(FAILED_PREFIX)) {
                return SeckillResultVO.of(SeckillResultVO.FAILED, cached.substring(FAILED_PREFIX.length()), null);
            }
        }

        Boolean joined = redisTemplate.opsForSet().isMember(RedisKeys.users(voucherId), String.valueOf(userId));
        if (Boolean.TRUE.equals(joined)) {
            SeckillOrder order = orderService.findOrder(voucherId, userId);
            if (order != null) {
                redisTemplate.opsForValue().set(resultKey, SUCCESS_PREFIX + order.getOrderNo(),
                        RESULT_TTL_HOURS, TimeUnit.HOURS);
                return SeckillResultVO.of(SeckillResultVO.SUCCESS, "抢购成功", order.getOrderNo());
            }
            return SeckillResultVO.of(SeckillResultVO.QUEUING, "订单处理中，请稍后查询", null);
        }

        VoucherLocalCache.LocalVoucher voucher = localCache.get(voucherId);
        if (voucher != null && voucher.isSoldOut()) {
            return SeckillResultVO.of(SeckillResultVO.SOLD_OUT, "商品已售罄", null);
        }
        return SeckillResultVO.of(SeckillResultVO.NOT_JOINED, "未参与本次抢购", null);
    }

    public void restoreStock(Long voucherId, Long userId) {
        try {
            redisTemplate.execute(restoreStockScript,
                    Arrays.asList(RedisKeys.stock(voucherId), RedisKeys.users(voucherId)),
                    String.valueOf(userId));
            localCache.clearSoldOut(voucherId);
        } catch (Exception e) {
            log.error("回补 Redis 库存失败 voucherId={}, userId={}", voucherId, userId, e);
        }
    }

    public void initStock() {
        for (VoucherLocalCache.LocalVoucher voucher : localCache.all()) {
            SeckillVoucher dbVoucher = voucherMapper.selectById(voucher.getId());
            if (dbVoucher == null) {
                continue;
            }
            String stockKey = RedisKeys.stock(voucher.getId());
            if (!Boolean.TRUE.equals(redisTemplate.hasKey(stockKey))) {
                redisTemplate.opsForValue().set(stockKey, String.valueOf(dbVoucher.getStock()));
            }
            long ttl = ttlSeconds(voucher);
            redisTemplate.expire(stockKey, ttl, TimeUnit.SECONDS);
            Long usersTtl = redisTemplate.getExpire(RedisKeys.users(voucher.getId()), TimeUnit.SECONDS);
            if (usersTtl != null && usersTtl < 0) {
                redisTemplate.expire(RedisKeys.users(voucher.getId()), ttl, TimeUnit.SECONDS);
            }
            if (dbVoucher.getStock() <= 0) {
                localCache.markSoldOut(voucher.getId());
            } else {
                localCache.clearSoldOut(voucher.getId());
            }
        }
    }

    private long ttlSeconds(VoucherLocalCache.LocalVoucher voucher) {
        long remainMs = voucher.getEndMs() - System.currentTimeMillis();
        return Math.max(3600L, remainMs / 1000L + 3600L);
    }
}
