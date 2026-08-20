package com.aninditb.shortlink.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;

public record CreateShortUrlRequest(
        @NotBlank String originalUrl,
        @Pattern(regexp = "[A-Za-z0-9_-]{3,32}") String customAlias,
        Instant expiresAt
) {
}
