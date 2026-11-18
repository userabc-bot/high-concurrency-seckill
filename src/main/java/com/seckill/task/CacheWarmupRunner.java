package com.seckill.task;

import com.seckill.service.SeckillService;
import com.seckill.service.VoucherLocalCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class CacheWarmupRunner implements ApplicationRunner {

    private final VoucherLocalCache localCache;
    private final SeckillService seckillService;
    private final AtomicBoolean warmed = new AtomicBoolean(false);

    @Override
    public void run(ApplicationArguments args) {
        warmup();
    }

    @Scheduled(fixedDelay = 5000, initialDelay = 5000)
    public void ensureWarmup() {
        if (!warmed.get()) {
            warmup();
        }
    }

    @Scheduled(fixedDelay = 30000, initialDelay = 30000)
    public void refreshMetadata() {
        if (warmed.get()) {
            localCache.refresh();
        }
    }

    private synchronized void warmup() {
        if (warmed.get()) {
            return;
        }
        try {
            localCache.refresh();
            seckillService.initStock();
            warmed.set(true);
            log.info("秒杀活动缓存预热完成，活动数量={}", localCache.all().size());
        } catch (Exception e) {
            log.warn("秒杀缓存预热失败，5 秒后重试: {}", e.getMessage());
        }
    }
}
