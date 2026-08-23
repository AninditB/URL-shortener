package com.aninditb.shortlink.service;

import com.aninditb.shortlink.dto.ShortUrlResponse;
import com.aninditb.shortlink.exception.IdempotencyConflictException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;

@Component
public class IdempotencyService {

    private static final String KEY_PREFIX = "idempotency:";
    private static final String IN_PROGRESS_PREFIX = "IN_PROGRESS:";
    private static final Duration RESERVATION_TTL = Duration.ofSeconds(30);
    private static final int MAX_POLL_ATTEMPTS = 10;
    private static final long POLL_INTERVAL_MS = 200;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public IdempotencyService(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${app.idempotency.ttl-hours}") long ttlHours
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.ttl = Duration.ofHours(ttlHours);
    }

    // Atomically claims the key (Redis SETNX) before any work happens, closing the race where
    // two concurrent requests with the same key both observed "not yet used" and both proceeded -
    // empty means this call won the claim and must call complete(); a present value means a
    // completed request with the same key+body already exists and its response should be reused.
    public Optional<ShortUrlResponse> claim(String idempotencyKey, String bodyHash) {
        String redisKey = key(idempotencyKey);
        if (Boolean.TRUE.equals(redisTemplate.opsForValue()
                .setIfAbsent(redisKey, IN_PROGRESS_PREFIX + bodyHash, RESERVATION_TTL))) {
            return Optional.empty();
        }
        return awaitCompletion(redisKey, bodyHash);
    }

    public void complete(String idempotencyKey, String bodyHash, ShortUrlResponse response) {
        String json = serialize(new IdempotencyRecord(bodyHash, response));
        redisTemplate.opsForValue().set(key(idempotencyKey), json, ttl);
    }

    // Someone else already claimed this key - wait briefly for them to finish rather than letting
    // a second caller silently redo the work (the race this replaces).
    private Optional<ShortUrlResponse> awaitCompletion(String redisKey, String bodyHash) {
        for (int attempt = 0; attempt < MAX_POLL_ATTEMPTS; attempt++) {
            String stored = redisTemplate.opsForValue().get(redisKey);
            if (stored == null) {
                // the reservation vanished (TTL expired) without completing - try claiming it
                if (Boolean.TRUE.equals(redisTemplate.opsForValue()
                        .setIfAbsent(redisKey, IN_PROGRESS_PREFIX + bodyHash, RESERVATION_TTL))) {
                    return Optional.empty();
                }
            } else if (stored.startsWith(IN_PROGRESS_PREFIX)) {
                if (!stored.substring(IN_PROGRESS_PREFIX.length()).equals(bodyHash)) {
                    throw conflict();
                }
            } else {
                IdempotencyRecord record = deserialize(stored);
                if (!record.bodyHash().equals(bodyHash)) {
                    throw conflict();
                }
                return Optional.of(record.response());
            }
            sleep();
        }
        throw new IdempotencyConflictException(
                "A request with this Idempotency-Key is still being processed; retry shortly");
    }

    private static void sleep() {
        try {
            Thread.sleep(POLL_INTERVAL_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for idempotent request to complete", e);
        }
    }

    private static IdempotencyConflictException conflict() {
        return new IdempotencyConflictException("Idempotency-Key was already used with a different request body");
    }

    public static String hash(String requestBody) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(requestBody.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private String serialize(IdempotencyRecord record) {
        try {
            return objectMapper.writeValueAsString(record);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize idempotency record", e);
        }
    }

    private IdempotencyRecord deserialize(String json) {
        try {
            return objectMapper.readValue(json, IdempotencyRecord.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize idempotency record", e);
        }
    }

    private String key(String idempotencyKey) {
        return KEY_PREFIX + idempotencyKey;
    }

    private record IdempotencyRecord(String bodyHash, ShortUrlResponse response) {
    }
}
