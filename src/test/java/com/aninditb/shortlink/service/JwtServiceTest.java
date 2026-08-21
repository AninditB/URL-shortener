package com.aninditb.shortlink.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String TEST_SECRET = "unit-test-jwt-secret-value-needs-to-be-at-least-32-bytes-long";

    private final JwtService jwtService = new JwtService(TEST_SECRET, 60);

    @Test
    void signedTokenRoundTripsToOriginalClaims() {
        String token = jwtService.generateToken(42L, "USER");

        Claims claims = jwtService.parseClaims(token);

        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.get("role", String.class)).isEqualTo("USER");
    }

    @Test
    void rejectsTamperedToken() {
        String token = jwtService.generateToken(42L, "USER");
        String tampered = token.substring(0, token.length() - 1) + (token.endsWith("a") ? "b" : "a");

        assertThatThrownBy(() -> jwtService.parseClaims(tampered))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsExpiredToken() {
        JwtService alreadyExpiredIssuer = new JwtService(TEST_SECRET, -1);
        String token = alreadyExpiredIssuer.generateToken(42L, "USER");

        assertThatThrownBy(() -> jwtService.parseClaims(token))
                .isInstanceOf(ExpiredJwtException.class);
    }
}
