package com.aninditb.shortlink.dto;

import java.time.Instant;

public record UrlDetailsResponse(
        Long id,
        String shortCode,
        String originalUrl,
        String status,
        Instant createdAt,
        Instant updatedAt,
        Instant expiresAt
) {
}
