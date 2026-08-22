package com.aninditb.shortlink.controller;

import com.aninditb.shortlink.analytics.AnalyticsService;
import com.aninditb.shortlink.dto.AnalyticsResponse;
import com.aninditb.shortlink.dto.PagedUrlResponse;
import com.aninditb.shortlink.dto.ShortUrlResponse;
import com.aninditb.shortlink.dto.UrlDetailsResponse;
import com.aninditb.shortlink.exception.AliasAlreadyExistsException;
import com.aninditb.shortlink.exception.ForbiddenException;
import com.aninditb.shortlink.exception.IdempotencyConflictException;
import com.aninditb.shortlink.exception.UrlNotFoundException;
import com.aninditb.shortlink.service.IdempotencyService;
import com.aninditb.shortlink.service.JwtService;
import com.aninditb.shortlink.service.ShortUrlService;
import com.aninditb.shortlink.web.RateLimitInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ShortUrlController.class)
@AutoConfigureMockMvc(addFilters = false)
class ShortUrlControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ShortUrlService service;

    // JwtAuthenticationFilter/SecurityConfig are part of the web-layer slice; JwtService is
    // their only unsatisfied dependency, so it must be mocked even though this test never uses it.
    @MockBean
    private JwtService jwtService;

    // WebMvcConfig (a WebMvcConfigurer, part of the slice) requires a RateLimitInterceptor bean;
    // HandlerInterceptor isn't itself a retained @WebMvcTest type, so it must be mocked here too.
    // Its preHandle() defaults to false on a bare mock, which would 429 every create call below,
    // so stub it to always allow -- this class exercises the controller, not rate limiting.
    @MockBean
    private RateLimitInterceptor rateLimitInterceptor;

    // Constructor dependency of ShortUrlController; not stubbed by default so tests
    // that never send an Idempotency-Key header (the common case) don't need it.
    @MockBean
    private IdempotencyService idempotencyService;

    @MockBean
    private AnalyticsService analyticsService;

    @BeforeEach
    void allowAllRequestsThroughRateLimiter() {
        lenient().when(rateLimitInterceptor.preHandle(any(), any(), any())).thenReturn(true);
    }

    @Test
    void createReturns201WithBody() throws Exception {
        when(service.create(any())).thenReturn(new ShortUrlResponse("java", "http://localhost:8080/java"));

        mockMvc.perform(post("/api/v1/urls")
                        .contentType("application/json")
                        .content("{\"originalUrl\":\"https://example.com/products/java\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortCode").value("java"))
                .andExpect(jsonPath("$.shortUrl").value("http://localhost:8080/java"));

        verifyNoInteractions(idempotencyService);
    }

    @Test
    void createWithFreshIdempotencyKeyStoresResponse() throws Exception {
        when(idempotencyService.findExisting(eq("key-1"), anyString())).thenReturn(Optional.empty());
        when(service.create(any())).thenReturn(new ShortUrlResponse("java", "http://localhost:8080/java"));

        mockMvc.perform(post("/api/v1/urls")
                        .header("Idempotency-Key", "key-1")
                        .contentType("application/json")
                        .content("{\"originalUrl\":\"https://example.com/products/java\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortCode").value("java"));

        verify(idempotencyService).store(eq("key-1"), anyString(), any(ShortUrlResponse.class));
    }

    @Test
    void createReplayedWithSameKeyAndBodyReturnsStoredResponseWithoutCallingService() throws Exception {
        ShortUrlResponse stored = new ShortUrlResponse("java", "http://localhost:8080/java");
        when(idempotencyService.findExisting(eq("key-1"), anyString())).thenReturn(Optional.of(stored));

        mockMvc.perform(post("/api/v1/urls")
                        .header("Idempotency-Key", "key-1")
                        .contentType("application/json")
                        .content("{\"originalUrl\":\"https://example.com/products/java\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortCode").value("java"));

        verify(service, never()).create(any());
    }

    @Test
    void createReplayedWithSameKeyAndDifferentBodyReturns409() throws Exception {
        when(idempotencyService.findExisting(eq("key-1"), anyString()))
                .thenThrow(new IdempotencyConflictException("Idempotency-Key 'key-1' was already used with a different request body"));

        mockMvc.perform(post("/api/v1/urls")
                        .header("Idempotency-Key", "key-1")
                        .contentType("application/json")
                        .content("{\"originalUrl\":\"https://example.com/different\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void createWithBlankOriginalUrlReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/urls")
                        .contentType("application/json")
                        .content("{\"originalUrl\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createWithTakenAliasReturns409() throws Exception {
        when(service.create(any())).thenThrow(new AliasAlreadyExistsException("Alias already in use: java"));

        mockMvc.perform(post("/api/v1/urls")
                        .contentType("application/json")
                        .content("{\"originalUrl\":\"https://example.com/x\",\"customAlias\":\"java\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void getDetailsReturns200() throws Exception {
        when(service.getDetails(eq(42L))).thenReturn(new UrlDetailsResponse(
                42L, "java", "https://example.com/products/java", "ACTIVE",
                Instant.parse("2026-08-20T04:00:00Z"), Instant.parse("2026-08-20T04:00:00Z"), null
        ));

        mockMvc.perform(get("/api/v1/urls/42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortCode").value("java"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void getDetailsWhenMissingReturns404() throws Exception {
        when(service.getDetails(eq(99L))).thenThrow(new UrlNotFoundException("No URL found for id 99"));

        mockMvc.perform(get("/api/v1/urls/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/v1/urls/42"))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteWhenNotOwnerReturns403() throws Exception {
        doThrow(new ForbiddenException("You do not have permission to delete this URL"))
                .when(service).delete(42L);

        mockMvc.perform(delete("/api/v1/urls/42"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void disableReturns204() throws Exception {
        mockMvc.perform(post("/api/v1/urls/42/disable"))
                .andExpect(status().isNoContent());
    }

    @Test
    void disableWhenNotOwnerReturns403() throws Exception {
        doThrow(new ForbiddenException("You do not have permission to modify this URL"))
                .when(service).disable(42L);

        mockMvc.perform(post("/api/v1/urls/42/disable"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void listOwnUrlsReturns200WithItemsAndNextCursor() throws Exception {
        UrlDetailsResponse item = new UrlDetailsResponse(
                42L, "java", "https://example.com/products/java", "ACTIVE",
                Instant.parse("2026-08-20T04:00:00Z"), Instant.parse("2026-08-20T04:00:00Z"), null
        );
        when(service.listOwnUrls(anyInt(), isNull())).thenReturn(new PagedUrlResponse(List.of(item), 41L));

        mockMvc.perform(get("/api/v1/urls"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].shortCode").value("java"))
                .andExpect(jsonPath("$.nextCursor").value(41));
    }

    @Test
    void getAnalyticsReturns200() throws Exception {
        when(analyticsService.getAnalytics(eq(42L))).thenReturn(new AnalyticsResponse(
                5L, java.util.Map.of("2026-08-20", 5L), java.util.Map.of("US", 5L), java.util.Map.of("DESKTOP", 5L)));

        mockMvc.perform(get("/api/v1/urls/42/analytics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClicks").value(5))
                .andExpect(jsonPath("$.topCountries.US").value(5));
    }

    @Test
    void getAnalyticsWhenNotOwnerReturns403() throws Exception {
        when(analyticsService.getAnalytics(eq(42L)))
                .thenThrow(new ForbiddenException("You do not have permission to view this URL's analytics"));

        mockMvc.perform(get("/api/v1/urls/42/analytics"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void getAnalyticsWhenMissingReturns404() throws Exception {
        when(analyticsService.getAnalytics(eq(99L)))
                .thenThrow(new UrlNotFoundException("No URL found for id 99"));

        mockMvc.perform(get("/api/v1/urls/99/analytics"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
