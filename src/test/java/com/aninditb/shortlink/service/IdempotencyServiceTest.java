package com.aninditb.shortlink.service;

import com.aninditb.shortlink.dto.ShortUrlResponse;
import com.aninditb.shortlink.exception.IdempotencyConflictException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdempotencyServiceTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);

    private IdempotencyService idempotencyService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        idempotencyService = new IdempotencyService(redisTemplate, new ObjectMapper(), 24);
    }

    @Test
    void claimReturnsEmptyWhenThisCallWinsTheReservation() {
        when(valueOperations.setIfAbsent(eq("idempotency:abc"), eq("IN_PROGRESS:hash1"), any(Duration.class)))
                .thenReturn(true);

        assertThat(idempotencyService.claim("abc", "hash1")).isEmpty();
    }

    @Test
    void claimReturnsStoredResponseWhenCompletedAndHashMatches() {
        ShortUrlResponse response = new ShortUrlResponse("java", "http://localhost:8080/java");
        String stored = storedJsonFor("hash1", response);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);
        when(valueOperations.get("idempotency:abc")).thenReturn(stored);

        assertThat(idempotencyService.claim("abc", "hash1")).contains(response);
    }

    @Test
    void claimThrowsWhenCompletedHashDiffers() {
        ShortUrlResponse response = new ShortUrlResponse("java", "http://localhost:8080/java");
        String stored = storedJsonFor("hash1", response);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);
        when(valueOperations.get("idempotency:abc")).thenReturn(stored);

        assertThatThrownBy(() -> idempotencyService.claim("abc", "different-hash"))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void claimThrowsWhenAnotherRequestIsInProgressWithADifferentHash() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);
        when(valueOperations.get("idempotency:abc")).thenReturn("IN_PROGRESS:other-hash");

        assertThatThrownBy(() -> idempotencyService.claim("abc", "hash1"))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void claimPollsUntilAConcurrentInProgressRequestCompletes() {
        ShortUrlResponse response = new ShortUrlResponse("java", "http://localhost:8080/java");
        String completed = storedJsonFor("hash1", response);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);
        when(valueOperations.get("idempotency:abc")).thenReturn("IN_PROGRESS:hash1", completed);

        assertThat(idempotencyService.claim("abc", "hash1")).contains(response);
    }

    @Test
    void completeWritesValueWithConfiguredTtl() {
        idempotencyService.complete("abc", "hash1", new ShortUrlResponse("java", "http://localhost:8080/java"));

        verify(valueOperations).set(eq("idempotency:abc"), anyString(), eq(Duration.ofHours(24)));
    }

    @Test
    void hashIsDeterministicAndDistinguishesBody() {
        String h1 = IdempotencyService.hash("{\"originalUrl\":\"a\"}");
        String h2 = IdempotencyService.hash("{\"originalUrl\":\"a\"}");
        String h3 = IdempotencyService.hash("{\"originalUrl\":\"b\"}");

        assertThat(h1).isEqualTo(h2);
        assertThat(h1).isNotEqualTo(h3);
    }

    private String storedJsonFor(String bodyHash, ShortUrlResponse response) {
        idempotencyService.complete("abc", bodyHash, response);
        var captor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(eq("idempotency:abc"), captor.capture(), any(Duration.class));
        return captor.getValue();
    }
}
