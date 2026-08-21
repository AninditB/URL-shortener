package com.aninditb.shortlink.controller;

import com.aninditb.shortlink.dto.ShortUrlResponse;
import com.aninditb.shortlink.dto.UrlDetailsResponse;
import com.aninditb.shortlink.exception.AliasAlreadyExistsException;
import com.aninditb.shortlink.exception.UrlNotFoundException;
import com.aninditb.shortlink.service.ShortUrlService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ShortUrlController.class)
class ShortUrlControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ShortUrlService service;

    @Test
    void createReturns201WithBody() throws Exception {
        when(service.create(any())).thenReturn(new ShortUrlResponse("java", "http://localhost:8080/java"));

        mockMvc.perform(post("/api/v1/urls")
                        .contentType("application/json")
                        .content("{\"originalUrl\":\"https://example.com/products/java\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortCode").value("java"))
                .andExpect(jsonPath("$.shortUrl").value("http://localhost:8080/java"));
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
}
