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

    public Optional<ShortUrlResponse> findExisting(String idempotencyKey, String bodyHash) {
        String stored = redisTemplate.opsForValue().get(key(idempotencyKey));
        if (stored == null) {
            return Optional.empty();
        }

        IdempotencyRecord record = deserialize(stored);
        if (!record.bodyHash().equals(bodyHash)) {
            throw new IdempotencyConflictException(
                    "Idempotency-Key '" + idempotencyKey + "' was already used with a different request body");
        }
        return Optional.of(record.response());
    }

    public void store(String idempotencyKey, String bodyHash, ShortUrlResponse response) {
        String json = serialize(new IdempotencyRecord(bodyHash, response));
        redisTemplate.opsForValue().set(key(idempotencyKey), json, ttl);
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
