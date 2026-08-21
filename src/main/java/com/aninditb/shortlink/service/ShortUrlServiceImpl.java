package com.aninditb.shortlink.service;

import com.aninditb.shortlink.dto.CreateShortUrlRequest;
import com.aninditb.shortlink.dto.ShortUrlResponse;
import com.aninditb.shortlink.dto.UrlDetailsResponse;
import com.aninditb.shortlink.entity.ShortUrl;
import com.aninditb.shortlink.entity.UrlStatus;
import com.aninditb.shortlink.exception.AliasAlreadyExistsException;
import com.aninditb.shortlink.exception.ForbiddenException;
import com.aninditb.shortlink.exception.InvalidUrlException;
import com.aninditb.shortlink.exception.UrlExpiredException;
import com.aninditb.shortlink.exception.UrlNotFoundException;
import com.aninditb.shortlink.repository.ShortUrlRepository;
import com.aninditb.shortlink.util.Base62Encoder;
import com.aninditb.shortlink.validation.UrlSafetyValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class ShortUrlServiceImpl implements ShortUrlService {

    private static final String CACHE_KEY_PREFIX = "shortcode:";
    private static final Duration DEFAULT_CACHE_TTL = Duration.ofHours(1);

    private final ShortUrlRepository repository;
    private final UrlSafetyValidator validator;
    private final StringRedisTemplate redisTemplate;
    private final String baseUrl;

    public ShortUrlServiceImpl(
            ShortUrlRepository repository,
            UrlSafetyValidator validator,
            StringRedisTemplate redisTemplate,
            @Value("${app.base-url}") String baseUrl
    ) {
        this.repository = repository;
        this.validator = validator;
        this.redisTemplate = redisTemplate;
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
            entity.setOwnerId(currentUserId());
            entity = repository.save(entity);
        } else {
            entity = new ShortUrl(request.originalUrl(), request.expiresAt());
            entity.setShortCode("tmp-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20));
            entity.setOwnerId(currentUserId());
            entity = repository.save(entity);
            entity.setShortCode(Base62Encoder.encode(entity.getId()));
            entity = repository.save(entity);
        }

        return new ShortUrlResponse(entity.getShortCode(), baseUrl + "/" + entity.getShortCode());
    }

    @Override
    @Transactional
    public String resolve(String shortCode) {
        String cacheKey = cacheKey(shortCode);
        String cachedUrl = redisTemplate.opsForValue().get(cacheKey);
        if (cachedUrl != null) {
            return cachedUrl;
        }

        ShortUrl entity = repository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException("No URL found for code '" + shortCode + "'"));

        if (expired(entity)) {
            entity.setStatus(UrlStatus.EXPIRED);
            repository.save(entity);
            redisTemplate.delete(cacheKey);
            throw new UrlExpiredException("URL for code '" + shortCode + "' has expired");
        }

        cacheActiveUrl(entity);
        return entity.getOriginalUrl();
    }

    @Override
    @Transactional
    public UrlDetailsResponse getDetails(Long id) {
        ShortUrl entity = repository.findById(id)
                .orElseThrow(() -> new UrlNotFoundException("No URL found for id " + id));

        if (expired(entity)) {
            entity.setStatus(UrlStatus.EXPIRED);
            entity = repository.save(entity);
            redisTemplate.delete(cacheKey(entity.getShortCode()));
        }

        return toDetailsResponse(entity);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        ShortUrl entity = repository.findById(id)
                .orElseThrow(() -> new UrlNotFoundException("No URL found for id " + id));

        Long ownerId = entity.getOwnerId();
        if (ownerId != null && !ownerId.equals(currentUserId()) && !currentUserIsAdmin()) {
            throw new ForbiddenException("You do not have permission to delete this URL");
        }

        repository.delete(entity);
        redisTemplate.delete(cacheKey(entity.getShortCode()));
    }

    private boolean expired(ShortUrl entity) {
        return entity.getExpiresAt() != null && entity.getExpiresAt().isBefore(Instant.now());
    }

    private Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Object principal = authentication != null ? authentication.getPrincipal() : null;
        return principal instanceof Long userId ? userId : null;
    }

    private boolean currentUserIsAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
    }

    private void cacheActiveUrl(ShortUrl entity) {
        Duration ttl = entity.getExpiresAt() != null
                ? Duration.between(Instant.now(), entity.getExpiresAt())
                : DEFAULT_CACHE_TTL;
        if (ttl.isNegative() || ttl.isZero()) {
            return;
        }
        redisTemplate.opsForValue().set(cacheKey(entity.getShortCode()), entity.getOriginalUrl(), ttl);
    }

    private String cacheKey(String shortCode) {
        return CACHE_KEY_PREFIX + shortCode;
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
