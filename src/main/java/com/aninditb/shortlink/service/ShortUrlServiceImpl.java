package com.aninditb.shortlink.service;

import com.aninditb.shortlink.dto.CreateShortUrlRequest;
import com.aninditb.shortlink.dto.ShortUrlResponse;
import com.aninditb.shortlink.dto.UrlDetailsResponse;
import com.aninditb.shortlink.entity.ShortUrl;
import com.aninditb.shortlink.entity.UrlStatus;
import com.aninditb.shortlink.exception.AliasAlreadyExistsException;
import com.aninditb.shortlink.exception.InvalidUrlException;
import com.aninditb.shortlink.exception.UrlExpiredException;
import com.aninditb.shortlink.exception.UrlNotFoundException;
import com.aninditb.shortlink.repository.ShortUrlRepository;
import com.aninditb.shortlink.util.Base62Encoder;
import com.aninditb.shortlink.validation.UrlSafetyValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class ShortUrlServiceImpl implements ShortUrlService {

    private final ShortUrlRepository repository;
    private final UrlSafetyValidator validator;
    private final String baseUrl;

    public ShortUrlServiceImpl(
            ShortUrlRepository repository,
            UrlSafetyValidator validator,
            @Value("${app.base-url}") String baseUrl
    ) {
        this.repository = repository;
        this.validator = validator;
        this.baseUrl = baseUrl;
    }

    @Override
    @Transactional
    public ShortUrlResponse create(CreateShortUrlRequest request) {
        validator.validate(request.originalUrl());

        if (request.expiresAt() != null && !request.expiresAt().isAfter(Instant.now())) {
            throw new InvalidUrlException("expiresAt must be in the future");
        }

        ShortUrl entity;
        if (request.customAlias() != null) {
            validator.validateAlias(request.customAlias());
            if (repository.existsByShortCode(request.customAlias())) {
                throw new AliasAlreadyExistsException("Alias already in use: " + request.customAlias());
            }
            entity = new ShortUrl(request.originalUrl(), request.expiresAt());
            entity.setShortCode(request.customAlias());
            entity = repository.save(entity);
        } else {
            entity = new ShortUrl(request.originalUrl(), request.expiresAt());
            entity.setShortCode("tmp-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20));
            entity = repository.save(entity);
            entity.setShortCode(Base62Encoder.encode(entity.getId()));
            entity = repository.save(entity);
        }

        return new ShortUrlResponse(entity.getShortCode(), baseUrl + "/" + entity.getShortCode());
    }

    @Override
    @Transactional
    public ShortUrl resolve(String shortCode) {
        ShortUrl entity = repository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException("No URL found for code '" + shortCode + "'"));

        if (expired(entity)) {
            entity.setStatus(UrlStatus.EXPIRED);
            repository.save(entity);
            throw new UrlExpiredException("URL for code '" + shortCode + "' has expired");
        }

        return entity;
    }

    @Override
    @Transactional
    public UrlDetailsResponse getDetails(Long id) {
        ShortUrl entity = repository.findById(id)
                .orElseThrow(() -> new UrlNotFoundException("No URL found for id " + id));

        if (expired(entity)) {
            entity.setStatus(UrlStatus.EXPIRED);
            entity = repository.save(entity);
        }

        return toDetailsResponse(entity);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new UrlNotFoundException("No URL found for id " + id);
        }
        repository.deleteById(id);
    }

    private boolean expired(ShortUrl entity) {
        return entity.getExpiresAt() != null && entity.getExpiresAt().isBefore(Instant.now());
    }

    private UrlDetailsResponse toDetailsResponse(ShortUrl entity) {
        return new UrlDetailsResponse(
                entity.getId(),
                entity.getShortCode(),
                entity.getOriginalUrl(),
                entity.getStatus().name(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getExpiresAt()
        );
    }
}
