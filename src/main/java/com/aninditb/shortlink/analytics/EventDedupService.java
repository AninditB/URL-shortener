package com.aninditb.shortlink.analytics;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class EventDedupService {

    private static final String KEY_PREFIX = "processed-event:";

    private final StringRedisTemplate redisTemplate;
    private final Duration ttl;

    public EventDedupService(
            StringRedisTemplate redisTemplate,
            @Value("${app.analytics.dedup-ttl-days}") long ttlDays
    ) {
        this.redisTemplate = redisTemplate;
        this.ttl = Duration.ofDays(ttlDays);
    }

    public boolean markProcessed(String eventId) {
        Boolean firstTime = redisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + eventId, "1", ttl);
        return Boolean.TRUE.equals(firstTime);
    }

    public void unmark(String eventId) {
        redisTemplate.delete(KEY_PREFIX + eventId);
    }
}
