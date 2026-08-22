package com.aninditb.shortlink.analytics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EventDedupServiceTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);

    private EventDedupService dedupService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        dedupService = new EventDedupService(redisTemplate, 7);
    }

    @Test
    void firstTimeSeenReturnsTrueAndWritesWithConfiguredTtl() {
        when(valueOperations.setIfAbsent("processed-event:abc", "1", Duration.ofDays(7))).thenReturn(true);

        assertThat(dedupService.markProcessed("abc")).isTrue();
    }

    @Test
    void redeliveredEventReturnsFalse() {
        when(valueOperations.setIfAbsent(eq("processed-event:abc"), eq("1"), eq(Duration.ofDays(7))))
                .thenReturn(false);

        assertThat(dedupService.markProcessed("abc")).isFalse();
    }

    @Test
    void nullResultFromRedisIsTreatedAsNotFirstTime() {
        when(valueOperations.setIfAbsent(eq("processed-event:abc"), eq("1"), eq(Duration.ofDays(7))))
                .thenReturn(null);

        assertThat(dedupService.markProcessed("abc")).isFalse();
    }
}
