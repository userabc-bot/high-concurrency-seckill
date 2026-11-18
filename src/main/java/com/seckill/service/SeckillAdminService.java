package com.seckill.service;

import com.seckill.common.RedisKeys;
import com.seckill.entity.SeckillVoucher;
import com.seckill.mapper.SeckillOrderMapper;
import com.seckill.mapper.SeckillVoucherMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class SeckillAdminService {

    private final SeckillVoucherMapper voucherMapper;
    private final SeckillOrderMapper orderMapper;
    private final StringRedisTemplate redisTemplate;
    private final VoucherLocalCache localCache;

    @Transactional(rollbackFor = Exception.class)
    public void reset(Long voucherId, int stock) {
        SeckillVoucher voucher = voucherMapper.selectById(voucherId);
        if (voucher == null) {
            throw new IllegalArgumentException("秒杀活动不存在");
        }
        voucherMapper.resetStock(voucherId, stock);
        orderMapper.deleteByVoucherId(voucherId);

        localCache.refresh();
        localCache.clearSoldOut(voucherId);

        String stockKey = RedisKeys.stock(voucherId);
        redisTemplate.opsForValue().set(stockKey, String.valueOf(stock));
        redisTemplate.delete(RedisKeys.users(voucherId));
        long ttl = Math.max(3600L, (localCache.get(voucherId).getEndMs() - System.currentTimeMillis()) / 1000L + 3600L);
        redisTemplate.expire(stockKey, ttl, TimeUnit.SECONDS);
        deleteResultKeys(voucherId);
    }

    public Map<String, Object> stats(Long voucherId) {
        SeckillVoucher voucher = voucherMapper.selectById(voucherId);
        if (voucher == null) {
            throw new IllegalArgumentException("秒杀活动不存在");
        }
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("voucherId", voucherId);
        stats.put("dbStock", voucher.getStock());
        stats.put("dbOrderCount", orderMapper.countByVoucherId(voucherId));
        stats.put("dbUserCount", orderMapper.countDistinctUsers(voucherId));
        stats.put("redisStock", redisTemplate.opsForValue().get(RedisKeys.stock(voucherId)));
        Long userSetSize = redisTemplate.opsForSet().size(RedisKeys.users(voucherId));
        stats.put("userSetSize", userSetSize);
        VoucherLocalCache.LocalVoucher local = localCache.get(voucherId);
        stats.put("soldOutFlag", local != null && local.isSoldOut());
        return stats;
    }

    private void deleteResultKeys(Long voucherId) {
        List<String> keys = new ArrayList<>();
        ScanOptions options = ScanOptions.scanOptions().match(RedisKeys.resultPattern(voucherId)).count(500).build();
        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            cursor.forEachRemaining(keys::add);
        }
        if (!keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }
}
