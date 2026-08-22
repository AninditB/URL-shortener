package com.aninditb.shortlink.analytics;

import com.aninditb.shortlink.dto.AnalyticsResponse;
import com.aninditb.shortlink.entity.ShortUrl;
import com.aninditb.shortlink.entity.UrlClickCountry;
import com.aninditb.shortlink.entity.UrlClickDaily;
import com.aninditb.shortlink.exception.ForbiddenException;
import com.aninditb.shortlink.exception.UrlNotFoundException;
import com.aninditb.shortlink.repository.ShortUrlRepository;
import com.aninditb.shortlink.repository.UrlClickCountryRepository;
import com.aninditb.shortlink.repository.UrlClickDailyRepository;
import com.aninditb.shortlink.repository.UrlClickDeviceRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AnalyticsService {

    private static final int MAX_DAYS = 30;
    private static final int MAX_COUNTRIES = 10;

    private final ShortUrlRepository shortUrlRepository;
    private final UrlClickDailyRepository dailyRepository;
    private final UrlClickCountryRepository countryRepository;
    private final UrlClickDeviceRepository deviceRepository;

    public AnalyticsService(
            ShortUrlRepository shortUrlRepository,
            UrlClickDailyRepository dailyRepository,
            UrlClickCountryRepository countryRepository,
            UrlClickDeviceRepository deviceRepository
    ) {
        this.shortUrlRepository = shortUrlRepository;
        this.dailyRepository = dailyRepository;
        this.countryRepository = countryRepository;
        this.deviceRepository = deviceRepository;
    }

    public AnalyticsResponse getAnalytics(Long id) {
        ShortUrl entity = shortUrlRepository.findById(id)
                .orElseThrow(() -> new UrlNotFoundException("No URL found for id " + id));

        requireOwnerOrAdmin(entity);

        Map<String, Long> clicksByDay = dailyRepository.findByShortUrlId(id).stream()
                .sorted(Comparator.comparing(UrlClickDaily::getClickDate).reversed())
                .limit(MAX_DAYS)
                .collect(Collectors.toMap(
                        row -> row.getClickDate().toString(), UrlClickDaily::getClickCount,
                        (a, b) -> a, LinkedHashMap::new));

        Map<String, Long> topCountries = countryRepository.findByShortUrlId(id).stream()
                .sorted(Comparator.comparingLong(UrlClickCountry::getClickCount).reversed())
                .limit(MAX_COUNTRIES)
                .collect(Collectors.toMap(
                        UrlClickCountry::getCountry, UrlClickCountry::getClickCount,
                        (a, b) -> a, LinkedHashMap::new));

        Map<String, Long> devices = deviceRepository.findByShortUrlId(id).stream()
                .collect(Collectors.toMap(
                        row -> row.getDeviceType(), row -> row.getClickCount(),
                        (a, b) -> a, LinkedHashMap::new));

        return new AnalyticsResponse(entity.getTotalClicks(), clicksByDay, topCountries, devices);
    }

    private void requireOwnerOrAdmin(ShortUrl entity) {
        Long ownerId = entity.getOwnerId();
        if (ownerId != null && !ownerId.equals(currentUserId()) && !currentUserIsAdmin()) {
            throw new ForbiddenException("You do not have permission to view this URL's analytics");
        }
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
}
