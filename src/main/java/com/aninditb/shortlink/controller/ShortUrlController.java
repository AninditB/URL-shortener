package com.aninditb.shortlink.controller;

import com.aninditb.shortlink.dto.CreateShortUrlRequest;
import com.aninditb.shortlink.dto.ShortUrlResponse;
import com.aninditb.shortlink.dto.UrlDetailsResponse;
import com.aninditb.shortlink.service.ShortUrlService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/urls")
public class ShortUrlController {

    private final ShortUrlService service;

    public ShortUrlController(ShortUrlService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ShortUrlResponse> create(@Valid @RequestBody CreateShortUrlRequest request) {
        return ResponseEntity.status(201).body(service.create(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UrlDetailsResponse> getDetails(@PathVariable Long id) {
        return ResponseEntity.ok(service.getDetails(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
