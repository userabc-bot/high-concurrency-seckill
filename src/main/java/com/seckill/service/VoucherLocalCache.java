package com.seckill.service;

import com.seckill.entity.SeckillVoucher;
import com.seckill.mapper.SeckillVoucherMapper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@RequiredArgsConstructor
public class VoucherLocalCache {

    private final SeckillVoucherMapper voucherMapper;

    private volatile Map<Long, LocalVoucher> cache = Collections.emptyMap();

    public void refresh() {
        List<SeckillVoucher> vouchers = voucherMapper.selectList(null);
        Map<Long, LocalVoucher> next = new HashMap<>(vouchers.size() * 2);
        Map<Long, LocalVoucher> current = cache;
        for (SeckillVoucher voucher : vouchers) {
            next.put(voucher.getId(), new LocalVoucher(voucher, current.get(voucher.getId())));
        }
        this.cache = next;
    }

    public LocalVoucher get(Long voucherId) {
        return cache.get(voucherId);
    }

    public Collection<LocalVoucher> all() {
        return cache.values();
    }

    public void markSoldOut(Long voucherId) {
        LocalVoucher voucher = cache.get(voucherId);
        if (voucher != null) {
            voucher.soldOut.set(true);
        }
    }

    public void clearSoldOut(Long voucherId) {
        LocalVoucher voucher = cache.get(voucherId);
        if (voucher != null) {
            voucher.soldOut.set(false);
        }
    }

    @Getter
    public static class LocalVoucher {

        private final Long id;
        private final String title;
        private final java.math.BigDecimal price;
        private final long beginMs;
        private final long endMs;
        private final AtomicBoolean soldOut;

        LocalVoucher(SeckillVoucher voucher, LocalVoucher old) {
            this.id = voucher.getId();
            this.title = voucher.getTitle();
            this.price = voucher.getPrice();
            this.beginMs = toMillis(voucher.getBeginTime());
            this.endMs = toMillis(voucher.getEndTime());
            this.soldOut = old != null ? old.soldOut : new AtomicBoolean(voucher.getStock() <= 0);
        }

        public boolean isSoldOut() {
            return soldOut.get();
        }

        public boolean inProgress(long nowMs) {
            return nowMs >= beginMs && nowMs <= endMs;
        }

        private static long toMillis(LocalDateTime time) {
            return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        }
    }
}
