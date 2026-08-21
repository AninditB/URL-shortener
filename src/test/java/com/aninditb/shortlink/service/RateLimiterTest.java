package com.aninditb.shortlink.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RateLimiterTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);

    private RateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        rateLimiter = new RateLimiter(redisTemplate, 60);
    }

    @Test
    void allowsRequestAtOrUnderLimit() {
        when(valueOperations.increment(anyString())).thenReturn(3L);

        assertThat(rateLimiter.tryAcquire("ip:1.2.3.4", 3)).isTrue();
    }

    @Test
    void deniesRequestOverLimit() {
        when(valueOperations.increment(anyString())).thenReturn(4L);

        assertThat(rateLimiter.tryAcquire("ip:1.2.3.4", 3)).isFalse();
    }

    @Test
    void setsExpiryOnlyOnFirstRequestInWindow() {
        when(valueOperations.increment(anyString())).thenReturn(1L);

        rateLimiter.tryAcquire("ip:1.2.3.4", 10);

        verify(redisTemplate).expire(anyString(), eq(Duration.ofSeconds(60)));
    }

    @Test
    void doesNotResetExpiryOnSubsequentRequestsInSameWindow() {
        when(valueOperations.increment(anyString())).thenReturn(2L);

        rateLimiter.tryAcquire("ip:1.2.3.4", 10);

        verify(redisTemplate, never()).expire(anyString(), any(Duration.class));
    }
}
