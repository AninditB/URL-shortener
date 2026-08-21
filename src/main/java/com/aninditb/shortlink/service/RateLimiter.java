package com.aninditb.shortlink.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class RateLimiter {

    private static final String KEY_PREFIX = "ratelimit:";

    private final StringRedisTemplate redisTemplate;
    private final Duration window;

    public RateLimiter(
            StringRedisTemplate redisTemplate,
            @Value("${app.rate-limit.window-seconds}") long windowSeconds
    ) {
        this.redisTemplate = redisTemplate;
        this.window = Duration.ofSeconds(windowSeconds);
    }

    public boolean tryAcquire(String identity, int maxRequests) {
        long windowStart = Instant.now().getEpochSecond() / window.getSeconds();
        String key = KEY_PREFIX + identity + ":" + windowStart;

        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, window);
        }

        return count != null && count <= maxRequests;
    }
}
