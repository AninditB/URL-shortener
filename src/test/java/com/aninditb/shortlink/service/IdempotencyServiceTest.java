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
    void findExistingReturnsEmptyWhenNoStoredValue() {
        when(valueOperations.get("idempotency:abc")).thenReturn(null);

        assertThat(idempotencyService.findExisting("abc", "hash1")).isEmpty();
    }

    @Test
    void findExistingReturnsStoredResponseWhenHashMatches() {
        ShortUrlResponse response = new ShortUrlResponse("java", "http://localhost:8080/java");
        String stored = storedJsonFor("hash1", response);
        when(valueOperations.get("idempotency:abc")).thenReturn(stored);

        Optional<ShortUrlResponse> result = idempotencyService.findExisting("abc", "hash1");

        assertThat(result).contains(response);
    }

    @Test
    void findExistingThrowsWhenHashDiffers() {
        ShortUrlResponse response = new ShortUrlResponse("java", "http://localhost:8080/java");
        String stored = storedJsonFor("hash1", response);
        when(valueOperations.get("idempotency:abc")).thenReturn(stored);

        assertThatThrownBy(() -> idempotencyService.findExisting("abc", "different-hash"))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void storeWritesValueWithConfiguredTtl() {
        idempotencyService.store("abc", "hash1", new ShortUrlResponse("java", "http://localhost:8080/java"));

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
        idempotencyService.store("abc", bodyHash, response);
        var captor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(eq("idempotency:abc"), captor.capture(), any(Duration.class));
        return captor.getValue();
    }
}
