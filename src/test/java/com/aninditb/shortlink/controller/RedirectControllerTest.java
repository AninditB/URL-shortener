package com.aninditb.shortlink.controller;

import com.aninditb.shortlink.exception.UrlExpiredException;
import com.aninditb.shortlink.exception.UrlNotFoundException;
import com.aninditb.shortlink.service.JwtService;
import com.aninditb.shortlink.service.ShortUrlService;
import com.aninditb.shortlink.web.RateLimitInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RedirectController.class)
@AutoConfigureMockMvc(addFilters = false)
class RedirectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ShortUrlService service;

    // JwtAuthenticationFilter/SecurityConfig are part of the web-layer slice; JwtService is
    // their only unsatisfied dependency, so it must be mocked even though this test never uses it.
    @MockBean
    private JwtService jwtService;

    // WebMvcConfig (a WebMvcConfigurer, part of the slice) requires a RateLimitInterceptor bean;
    // not stubbed since these GET requests never match its /api/v1/urls path pattern anyway.
    @MockBean
    private RateLimitInterceptor rateLimitInterceptor;

    @Test
    void redirectsToOriginalUrlWhenActive() throws Exception {
        when(service.resolve("java")).thenReturn("https://example.com/products/java");

        mockMvc.perform(get("/java"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/products/java"));
    }

    @Test
    void returns404WhenCodeMissing() throws Exception {
        when(service.resolve("missing")).thenThrow(new UrlNotFoundException("No URL found for code 'missing'"));

        mockMvc.perform(get("/missing"))
                .andExpect(status().isNotFound());
    }

    @Test
    void returns410WhenExpired() throws Exception {
        when(service.resolve("java")).thenThrow(new UrlExpiredException("URL for code 'java' has expired"));

        mockMvc.perform(get("/java"))
                .andExpect(status().isGone());
    }
}
