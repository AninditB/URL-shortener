package com.aninditb.shortlink.web;

import com.aninditb.shortlink.exception.RateLimitExceededException;
import com.aninditb.shortlink.service.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiter rateLimiter;
    private final int anonymousLimit;
    private final int authenticatedLimit;

    public RateLimitInterceptor(
            RateLimiter rateLimiter,
            @Value("${app.rate-limit.anonymous-limit}") int anonymousLimit,
            @Value("${app.rate-limit.authenticated-limit}") int authenticatedLimit
    ) {
        this.rateLimiter = rateLimiter;
        this.anonymousLimit = anonymousLimit;
        this.authenticatedLimit = authenticatedLimit;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        Long userId = currentUserId();
        String identity = userId != null ? "user:" + userId : "ip:" + request.getRemoteAddr();
        int limit = userId != null ? authenticatedLimit : anonymousLimit;

        if (!rateLimiter.tryAcquire(identity, limit)) {
            throw new RateLimitExceededException("Rate limit exceeded, try again later");
        }

        return true;
    }

    private Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Object principal = authentication != null ? authentication.getPrincipal() : null;
        return principal instanceof Long userId ? userId : null;
    }
}
