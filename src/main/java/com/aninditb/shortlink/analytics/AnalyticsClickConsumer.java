package com.aninditb.shortlink.analytics;

import com.aninditb.shortlink.config.KafkaConfig;
import com.aninditb.shortlink.entity.ShortUrl;
import com.aninditb.shortlink.repository.ShortUrlRepository;
import com.aninditb.shortlink.repository.UrlClickCountryRepository;
import com.aninditb.shortlink.repository.UrlClickDailyRepository;
import com.aninditb.shortlink.repository.UrlClickDeviceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;

@Component
public class AnalyticsClickConsumer {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsClickConsumer.class);

    private final EventDedupService dedupService;
    private final ShortUrlRepository shortUrlRepository;
    private final UrlClickDailyRepository dailyRepository;
    private final UrlClickCountryRepository countryRepository;
    private final UrlClickDeviceRepository deviceRepository;

    public AnalyticsClickConsumer(
            EventDedupService dedupService,
            ShortUrlRepository shortUrlRepository,
            UrlClickDailyRepository dailyRepository,
            UrlClickCountryRepository countryRepository,
            UrlClickDeviceRepository deviceRepository
    ) {
        this.dedupService = dedupService;
        this.shortUrlRepository = shortUrlRepository;
        this.dailyRepository = dailyRepository;
        this.countryRepository = countryRepository;
        this.deviceRepository = deviceRepository;
    }

    @KafkaListener(topics = KafkaConfig.CLICK_EVENTS_TOPIC, groupId = KafkaConfig.ANALYTICS_CONSUMER_GROUP)
    @Transactional
    public void onClickEvent(ClickEvent event) {
        if (!dedupService.markProcessed(event.eventId())) {
            log.debug("Skipping already-processed click event {}", event.eventId());
            return;
        }

        Long shortUrlId = shortUrlRepository.findByShortCode(event.shortCode())
                .map(ShortUrl::getId)
                .orElse(null);
        if (shortUrlId == null) {
            log.warn("Click event for unknown shortCode '{}', skipping aggregate update", event.shortCode());
            return;
        }

        shortUrlRepository.incrementTotalClicks(shortUrlId);
        dailyRepository.incrementDaily(shortUrlId, event.timestamp().atZone(ZoneOffset.UTC).toLocalDate());
        countryRepository.incrementCountry(shortUrlId, event.country());
        deviceRepository.incrementDevice(shortUrlId, event.deviceType());
    }
}
