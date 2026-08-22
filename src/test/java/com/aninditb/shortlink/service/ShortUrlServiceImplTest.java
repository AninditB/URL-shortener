package com.aninditb.shortlink.service;

import com.aninditb.shortlink.analytics.ClickEventPublisher;
import com.aninditb.shortlink.dto.CreateShortUrlRequest;
import com.aninditb.shortlink.dto.PagedUrlResponse;
import com.aninditb.shortlink.dto.ShortUrlResponse;
import com.aninditb.shortlink.dto.UrlDetailsResponse;
import com.aninditb.shortlink.entity.ShortUrl;
import com.aninditb.shortlink.entity.UrlStatus;
import com.aninditb.shortlink.exception.AliasAlreadyExistsException;
import com.aninditb.shortlink.exception.ForbiddenException;
import com.aninditb.shortlink.exception.UrlExpiredException;
import com.aninditb.shortlink.exception.UrlNotFoundException;
import com.aninditb.shortlink.repository.ShortUrlRepository;
import com.aninditb.shortlink.validation.UrlSafetyValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShortUrlServiceImplTest {

    @Mock
    private ShortUrlRepository repository;

    @Mock
    private UrlSafetyValidator validator;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ClickEventPublisher clickEventPublisher;

    @InjectMocks
    private ShortUrlServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        Field field = ShortUrlServiceImpl.class.getDeclaredField("baseUrl");
        field.setAccessible(true);
        field.set(service, "http://localhost:8080");

        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static void authenticateAs(long userId, String... roles) {
        List<SimpleGrantedAuthority> authorities = List.of(roles).stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, authorities));
    }

    @Test
    void createWithoutAliasEncodesGeneratedId() {
        CreateShortUrlRequest request = new CreateShortUrlRequest("https://example.com/x", null, null);
        doNothing().when(validator).validate(request.originalUrl());

        when(repository.save(any(ShortUrl.class))).thenAnswer(invocation -> {
            ShortUrl entity = invocation.getArgument(0);
            setId(entity, 62L);
            return entity;
        });

        ShortUrlResponse response = service.create(request);

        assertThat(response.shortCode()).isEqualTo("10");
        assertThat(response.shortUrl()).isEqualTo("http://localhost:8080/10");
    }

    @Test
    void createWithAliasUsesAliasAsShortCode() {
        CreateShortUrlRequest request = new CreateShortUrlRequest("https://example.com/x", "java", null);
        doNothing().when(validator).validate(request.originalUrl());
        doNothing().when(validator).validateAlias("java");
        when(repository.existsByShortCode("java")).thenReturn(false);
        when(repository.save(any(ShortUrl.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ShortUrlResponse response = service.create(request);

        assertThat(response.shortCode()).isEqualTo("java");
        assertThat(response.shortUrl()).isEqualTo("http://localhost:8080/java");
    }

    @Test
    void createWithTakenAliasThrowsConflict() {
        CreateShortUrlRequest request = new CreateShortUrlRequest("https://example.com/x", "java", null);
        doNothing().when(validator).validate(request.originalUrl());
        doNothing().when(validator).validateAlias("java");
        when(repository.existsByShortCode("java")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(AliasAlreadyExistsException.class);
    }

    @Test
    void createSetsOwnerIdWhenAuthenticated() {
        authenticateAs(9L, "USER");
        CreateShortUrlRequest request = new CreateShortUrlRequest("https://example.com/x", "java", null);
        doNothing().when(validator).validate(request.originalUrl());
        doNothing().when(validator).validateAlias("java");
        when(repository.existsByShortCode("java")).thenReturn(false);
        ArgumentCaptor<ShortUrl> captor = ArgumentCaptor.forClass(ShortUrl.class);
        when(repository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(request);

        assertThat(captor.getValue().getOwnerId()).isEqualTo(9L);
    }

    @Test
    void createLeavesOwnerNullWhenAnonymous() {
        CreateShortUrlRequest request = new CreateShortUrlRequest("https://example.com/x", "java", null);
        doNothing().when(validator).validate(request.originalUrl());
        doNothing().when(validator).validateAlias("java");
        when(repository.existsByShortCode("java")).thenReturn(false);
        ArgumentCaptor<ShortUrl> captor = ArgumentCaptor.forClass(ShortUrl.class);
        when(repository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(request);

        assertThat(captor.getValue().getOwnerId()).isNull();
    }

    @Test
    void resolveOnCacheHitSkipsDatabase() {
        when(valueOperations.get("shortcode:java")).thenReturn("https://example.com/x");

        String resolved = service.resolve("java");

        assertThat(resolved).isEqualTo("https://example.com/x");
        verify(repository, never()).findByShortCode(anyString());
        verify(clickEventPublisher).publish("java");
    }

    @Test
    void resolveOnCacheMissFallsBackToDatabaseAndPopulatesCache() {
        ShortUrl entity = new ShortUrl("https://example.com/x", null);
        entity.setShortCode("java");
        when(valueOperations.get("shortcode:java")).thenReturn(null);
        when(repository.findByShortCode("java")).thenReturn(Optional.of(entity));

        String resolved = service.resolve("java");

        assertThat(resolved).isEqualTo("https://example.com/x");
        verify(valueOperations).set(eq("shortcode:java"), eq("https://example.com/x"), any());
        verify(clickEventPublisher).publish("java");
    }

    @Test
    void resolveThrowsNotFoundWhenMissing() {
        when(valueOperations.get("shortcode:missing")).thenReturn(null);
        when(repository.findByShortCode("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolve("missing"))
                .isInstanceOf(UrlNotFoundException.class);
    }

    @Test
    void resolveThrowsExpiredAndPersistsStatusAndEvictsCache() {
        ShortUrl entity = new ShortUrl("https://example.com/x", Instant.now().minus(1, ChronoUnit.DAYS));
        entity.setShortCode("java");
        when(valueOperations.get("shortcode:java")).thenReturn(null);
        when(repository.findByShortCode("java")).thenReturn(Optional.of(entity));
        when(repository.save(entity)).thenReturn(entity);

        assertThatThrownBy(() -> service.resolve("java"))
                .isInstanceOf(UrlExpiredException.class);

        assertThat(entity.getStatus()).isEqualTo(UrlStatus.EXPIRED);
        verify(repository).save(entity);
        verify(redisTemplate).delete("shortcode:java");
        verify(clickEventPublisher, never()).publish(anyString());
    }

    @Test
    void resolveThrowsExpiredForDisabledLink() {
        ShortUrl entity = new ShortUrl("https://example.com/x", null);
        entity.setShortCode("java");
        entity.setStatus(UrlStatus.DISABLED);
        when(valueOperations.get("shortcode:java")).thenReturn(null);
        when(repository.findByShortCode("java")).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.resolve("java"))
                .isInstanceOf(UrlExpiredException.class);
        verify(clickEventPublisher, never()).publish(anyString());
    }

    @Test
    void getDetailsOnExpiredLinkReturnsExpiredStatusWithoutThrowing() {
        ShortUrl entity = new ShortUrl("https://example.com/x", Instant.now().minus(1, ChronoUnit.DAYS));
        entity.setShortCode("java");
        setId(entity, 1L);
        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        when(repository.save(entity)).thenReturn(entity);

        UrlDetailsResponse response = service.getDetails(1L);

        assertThat(response.status()).isEqualTo("EXPIRED");
        verify(redisTemplate).delete("shortcode:java");
    }

    @Test
    void getDetailsOnDisabledLinkReturnsDisabledStatusWithoutThrowing() {
        ShortUrl entity = new ShortUrl("https://example.com/x", null);
        entity.setShortCode("java");
        entity.setStatus(UrlStatus.DISABLED);
        setId(entity, 1L);
        when(repository.findById(1L)).thenReturn(Optional.of(entity));

        UrlDetailsResponse response = service.getDetails(1L);

        assertThat(response.status()).isEqualTo("DISABLED");
    }

    @Test
    void deleteThrowsNotFoundWhenMissing() {
        when(repository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(UrlNotFoundException.class);

        verify(repository, never()).delete(any());
    }

    @Test
    void deleteRemovesExistingRowAndEvictsCache() {
        ShortUrl entity = new ShortUrl("https://example.com/x", null);
        entity.setShortCode("java");
        setId(entity, 1L);
        when(repository.findById(1L)).thenReturn(Optional.of(entity));

        service.delete(1L);

        verify(repository).delete(entity);
        verify(redisTemplate).delete("shortcode:java");
    }

    @Test
    void deleteByOwnerSucceeds() {
        authenticateAs(5L, "USER");
        ShortUrl entity = new ShortUrl("https://example.com/x", null);
        entity.setShortCode("java");
        entity.setOwnerId(5L);
        setId(entity, 1L);
        when(repository.findById(1L)).thenReturn(Optional.of(entity));

        service.delete(1L);

        verify(repository).delete(entity);
    }

    @Test
    void deleteByNonOwnerThrowsForbidden() {
        authenticateAs(6L, "USER");
        ShortUrl entity = new ShortUrl("https://example.com/x", null);
        entity.setShortCode("java");
        entity.setOwnerId(5L);
        setId(entity, 1L);
        when(repository.findById(1L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(ForbiddenException.class);

        verify(repository, never()).delete(any());
    }

    @Test
    void deleteByAdminSucceedsRegardlessOfOwnership() {
        authenticateAs(6L, "ADMIN");
        ShortUrl entity = new ShortUrl("https://example.com/x", null);
        entity.setShortCode("java");
        entity.setOwnerId(5L);
        setId(entity, 1L);
        when(repository.findById(1L)).thenReturn(Optional.of(entity));

        service.delete(1L);

        verify(repository).delete(entity);
    }

    @Test
    void deleteOfAnonymouslyOwnedUrlSucceedsForAnyAuthenticatedCaller() {
        authenticateAs(6L, "USER");
        ShortUrl entity = new ShortUrl("https://example.com/x", null);
        entity.setShortCode("java");
        setId(entity, 1L);
        when(repository.findById(1L)).thenReturn(Optional.of(entity));

        service.delete(1L);

        verify(repository).delete(entity);
    }

    @Test
    void disableThrowsNotFoundWhenMissing() {
        when(repository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.disable(1L))
                .isInstanceOf(UrlNotFoundException.class);
    }

    @Test
    void disableByOwnerSetsDisabledStatusAndEvictsCache() {
        authenticateAs(5L, "USER");
        ShortUrl entity = new ShortUrl("https://example.com/x", null);
        entity.setShortCode("java");
        entity.setOwnerId(5L);
        setId(entity, 1L);
        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        when(repository.save(entity)).thenReturn(entity);

        service.disable(1L);

        assertThat(entity.getStatus()).isEqualTo(UrlStatus.DISABLED);
        verify(redisTemplate).delete("shortcode:java");
    }

    @Test
    void disableByNonOwnerThrowsForbidden() {
        authenticateAs(6L, "USER");
        ShortUrl entity = new ShortUrl("https://example.com/x", null);
        entity.setShortCode("java");
        entity.setOwnerId(5L);
        setId(entity, 1L);
        when(repository.findById(1L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.disable(1L))
                .isInstanceOf(ForbiddenException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void disableByAdminSucceedsRegardlessOfOwnership() {
        authenticateAs(6L, "ADMIN");
        ShortUrl entity = new ShortUrl("https://example.com/x", null);
        entity.setShortCode("java");
        entity.setOwnerId(5L);
        setId(entity, 1L);
        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        when(repository.save(entity)).thenReturn(entity);

        service.disable(1L);

        assertThat(entity.getStatus()).isEqualTo(UrlStatus.DISABLED);
    }

    @Test
    void listOwnUrlsWithoutCursorUsesNoCursorQuery() {
        authenticateAs(9L, "USER");
        when(repository.findByOwnerIdOrderByIdDesc(eq(9L), any())).thenReturn(List.of());

        service.listOwnUrls(20, null);

        verify(repository).findByOwnerIdOrderByIdDesc(eq(9L), any());
        verify(repository, never()).findByOwnerIdAndIdLessThanOrderByIdDesc(any(), any(), any());
    }

    @Test
    void listOwnUrlsWithCursorUsesCursorQuery() {
        authenticateAs(9L, "USER");
        when(repository.findByOwnerIdAndIdLessThanOrderByIdDesc(eq(9L), eq(50L), any())).thenReturn(List.of());

        service.listOwnUrls(20, 50L);

        verify(repository).findByOwnerIdAndIdLessThanOrderByIdDesc(eq(9L), eq(50L), any());
    }

    @Test
    void listOwnUrlsCapsLimitAtOneHundred() {
        authenticateAs(9L, "USER");
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(repository.findByOwnerIdOrderByIdDesc(eq(9L), pageableCaptor.capture())).thenReturn(List.of());

        service.listOwnUrls(500, null);

        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    void listOwnUrlsSetsNextCursorWhenPageIsFull() {
        authenticateAs(9L, "USER");
        ShortUrl a = new ShortUrl("https://example.com/a", null);
        a.setShortCode("a");
        a.setStatus(UrlStatus.ACTIVE);
        setId(a, 10L);
        ShortUrl b = new ShortUrl("https://example.com/b", null);
        b.setShortCode("b");
        b.setStatus(UrlStatus.ACTIVE);
        setId(b, 9L);
        when(repository.findByOwnerIdOrderByIdDesc(eq(9L), any())).thenReturn(List.of(a, b));

        PagedUrlResponse response = service.listOwnUrls(2, null);

        assertThat(response.items()).hasSize(2);
        assertThat(response.nextCursor()).isEqualTo(9L);
    }

    @Test
    void listOwnUrlsLeavesNextCursorNullWhenPageIsShort() {
        authenticateAs(9L, "USER");
        ShortUrl a = new ShortUrl("https://example.com/a", null);
        a.setShortCode("a");
        a.setStatus(UrlStatus.ACTIVE);
        setId(a, 10L);
        when(repository.findByOwnerIdOrderByIdDesc(eq(9L), any())).thenReturn(List.of(a));

        PagedUrlResponse response = service.listOwnUrls(2, null);

        assertThat(response.nextCursor()).isNull();
    }

    private static void setId(ShortUrl entity, long id) {
        try {
            Field field = ShortUrl.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
