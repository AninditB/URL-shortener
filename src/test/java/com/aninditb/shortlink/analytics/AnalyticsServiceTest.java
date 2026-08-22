package com.aninditb.shortlink.analytics;

import com.aninditb.shortlink.dto.AnalyticsResponse;
import com.aninditb.shortlink.entity.ShortUrl;
import com.aninditb.shortlink.entity.UrlClickCountry;
import com.aninditb.shortlink.entity.UrlClickDaily;
import com.aninditb.shortlink.entity.UrlClickDevice;
import com.aninditb.shortlink.exception.ForbiddenException;
import com.aninditb.shortlink.exception.UrlNotFoundException;
import com.aninditb.shortlink.repository.ShortUrlRepository;
import com.aninditb.shortlink.repository.UrlClickCountryRepository;
import com.aninditb.shortlink.repository.UrlClickDailyRepository;
import com.aninditb.shortlink.repository.UrlClickDeviceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock
    private ShortUrlRepository shortUrlRepository;

    @Mock
    private UrlClickDailyRepository dailyRepository;

    @Mock
    private UrlClickCountryRepository countryRepository;

    @Mock
    private UrlClickDeviceRepository deviceRepository;

    @InjectMocks
    private AnalyticsService service;

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

    private static ShortUrl entityWithOwnerAndClicks(long ownerId, long totalClicks) {
        ShortUrl entity = new ShortUrl("https://example.com/x", null);
        entity.setShortCode("java");
        entity.setOwnerId(ownerId);
        setField(entity, "totalClicks", totalClicks);
        return entity;
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private void stubEmptyRollups(Long id) {
        lenient().when(dailyRepository.findByShortUrlId(id)).thenReturn(List.of());
        lenient().when(countryRepository.findByShortUrlId(id)).thenReturn(List.of());
        lenient().when(deviceRepository.findByShortUrlId(id)).thenReturn(List.of());
    }

    @Test
    void ownerCanReadOwnAnalytics() {
        authenticateAs(5L, "USER");
        when(shortUrlRepository.findById(1L)).thenReturn(Optional.of(entityWithOwnerAndClicks(5L, 3L)));
        stubEmptyRollups(1L);

        AnalyticsResponse response = service.getAnalytics(1L);

        assertThat(response.totalClicks()).isEqualTo(3L);
    }

    @Test
    void adminCanReadAnyonesAnalytics() {
        authenticateAs(6L, "ADMIN");
        when(shortUrlRepository.findById(1L)).thenReturn(Optional.of(entityWithOwnerAndClicks(5L, 3L)));
        stubEmptyRollups(1L);

        AnalyticsResponse response = service.getAnalytics(1L);

        assertThat(response.totalClicks()).isEqualTo(3L);
    }

    @Test
    void nonOwnerNonAdminThrowsForbidden() {
        authenticateAs(6L, "USER");
        when(shortUrlRepository.findById(1L)).thenReturn(Optional.of(entityWithOwnerAndClicks(5L, 3L)));

        assertThatThrownBy(() -> service.getAnalytics(1L)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void missingUrlThrowsNotFound() {
        authenticateAs(5L, "USER");
        when(shortUrlRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getAnalytics(99L)).isInstanceOf(UrlNotFoundException.class);
    }

    @Test
    void clicksByDayIsCappedTo30MostRecentDaysAndTopCountriesTo10ByCount() {
        authenticateAs(5L, "USER");
        when(shortUrlRepository.findById(1L)).thenReturn(Optional.of(entityWithOwnerAndClicks(5L, 100L)));

        List<UrlClickDaily> days = java.util.stream.IntStream.range(0, 35)
                .mapToObj(i -> new UrlClickDaily(1L, LocalDate.of(2026, 1, 1).plusDays(i), i))
                .toList();
        when(dailyRepository.findByShortUrlId(1L)).thenReturn(days);

        List<UrlClickCountry> countries = java.util.stream.IntStream.range(0, 12)
                .mapToObj(i -> new UrlClickCountry(1L, "C" + i, i))
                .toList();
        when(countryRepository.findByShortUrlId(1L)).thenReturn(countries);

        when(deviceRepository.findByShortUrlId(1L)).thenReturn(
                List.of(new UrlClickDevice(1L, "DESKTOP", 7L)));

        AnalyticsResponse response = service.getAnalytics(1L);

        assertThat(response.clicksByDay()).hasSize(30);
        assertThat(response.clicksByDay()).containsKey(LocalDate.of(2026, 1, 1).plusDays(34).toString());
        assertThat(response.clicksByDay()).doesNotContainKey(LocalDate.of(2026, 1, 1).toString());

        assertThat(response.topCountries()).hasSize(10);
        assertThat(response.topCountries()).containsEntry("C11", 11L);
        assertThat(response.topCountries()).doesNotContainKey("C0");

        assertThat(response.devices()).containsEntry("DESKTOP", 7L);
    }
}
