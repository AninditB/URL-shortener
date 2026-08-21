package com.aninditb.shortlink.controller;

import com.aninditb.shortlink.dto.CreateShortUrlRequest;
import com.aninditb.shortlink.dto.PagedUrlResponse;
import com.aninditb.shortlink.dto.ShortUrlResponse;
import com.aninditb.shortlink.dto.UrlDetailsResponse;
import com.aninditb.shortlink.service.IdempotencyService;
import com.aninditb.shortlink.service.ShortUrlService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/api/v1/urls")
public class ShortUrlController {

    private final ShortUrlService service;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    public ShortUrlController(ShortUrlService service, IdempotencyService idempotencyService, ObjectMapper objectMapper) {
        this.service = service;
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<ShortUrlResponse> create(
            @Valid @RequestBody CreateShortUrlRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return ResponseEntity.status(201).body(service.create(request));
        }

        String bodyHash = IdempotencyService.hash(serialize(request));
        Optional<ShortUrlResponse> existing = idempotencyService.findExisting(idempotencyKey, bodyHash);
        if (existing.isPresent()) {
            return ResponseEntity.status(201).body(existing.get());
        }

        ShortUrlResponse response = service.create(request);
        idempotencyService.store(idempotencyKey, bodyHash, response);
        return ResponseEntity.status(201).body(response);
    }

    private String serialize(CreateShortUrlRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize request for idempotency hashing", e);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<UrlDetailsResponse> getDetails(@PathVariable Long id) {
        return ResponseEntity.ok(service.getDetails(id));
    }

    @GetMapping
    public ResponseEntity<PagedUrlResponse> listOwnUrls(
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) Long cursor
    ) {
        return ResponseEntity.ok(service.listOwnUrls(limit, cursor));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/disable")
    public ResponseEntity<Void> disable(@PathVariable Long id) {
        service.disable(id);
        return ResponseEntity.noContent().build();
    }
}
