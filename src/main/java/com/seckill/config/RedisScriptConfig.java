package com.seckill.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;

@Configuration
public class RedisScriptConfig {

    @Bean
    public DefaultRedisScript<Long> seckillScript() {
        return load("lua/seckill.lua");
    }

    @Bean
    public DefaultRedisScript<Long> rateLimitScript() {
        return load("lua/rate_limit.lua");
    }

    @Bean
    public DefaultRedisScript<Long> restoreStockScript() {
        return load("lua/restore_stock.lua");
    }

    private DefaultRedisScript<Long> load(String path) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(path));
        script.setResultType(Long.class);
        return script;
    }
}
