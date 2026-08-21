package com.aninditb.shortlink.web;

import com.aninditb.shortlink.exception.RateLimitExceededException;
import com.aninditb.shortlink.service.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RateLimitInterceptorTest {

    private final RateLimiter rateLimiter = mock(RateLimiter.class);
    private final RateLimitInterceptor interceptor = new RateLimitInterceptor(rateLimiter, 10, 100);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void allowsAnonymousRequestUnderLimit() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("POST");
        when(request.getRemoteAddr()).thenReturn("1.2.3.4");
        when(rateLimiter.tryAcquire("ip:1.2.3.4", 10)).thenReturn(true);

        boolean result = interceptor.preHandle(request, mock(HttpServletResponse.class), new Object());

        assertThat(result).isTrue();
    }

    @Test
    void throwsWhenAnonymousLimitExceeded() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("POST");
        when(request.getRemoteAddr()).thenReturn("1.2.3.4");
        when(rateLimiter.tryAcquire("ip:1.2.3.4", 10)).thenReturn(false);

        assertThatThrownBy(() -> interceptor.preHandle(request, mock(HttpServletResponse.class), new Object()))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void usesAuthenticatedUserIdAndHigherLimitWhenPresent() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                9L, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("POST");
        when(rateLimiter.tryAcquire("user:9", 100)).thenReturn(true);

        boolean result = interceptor.preHandle(request, mock(HttpServletResponse.class), new Object());

        assertThat(result).isTrue();
        verify(rateLimiter).tryAcquire("user:9", 100);
    }

    @Test
    void skipsRateLimitingForNonPostRequests() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("GET");

        boolean result = interceptor.preHandle(request, mock(HttpServletResponse.class), new Object());

        assertThat(result).isTrue();
        verifyNoInteractions(rateLimiter);
    }
}
