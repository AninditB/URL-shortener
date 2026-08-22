package com.aninditb.shortlink.analytics;

import com.aninditb.shortlink.config.KafkaConfig;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.UUID;

@Component
public class ClickEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ClickEventPublisher.class);

    private final KafkaTemplate<String, ClickEvent> kafkaTemplate;
    private final GeoCountryResolver geoCountryResolver;

    public ClickEventPublisher(KafkaTemplate<String, ClickEvent> kafkaTemplate, GeoCountryResolver geoCountryResolver) {
        this.kafkaTemplate = kafkaTemplate;
        this.geoCountryResolver = geoCountryResolver;
    }

    public void publish(String shortCode) {
        // Wrapped end-to-end, not just around the async Kafka send: building the event can
        // itself throw synchronously (e.g. GeoCountryResolver's first real lookup constructing
        // its lazily-initialized MaxMind reader against a bad/missing database path) - a
        // redirect must never fail because of anything in this pipeline (FR-3).
        try {
            HttpServletRequest request = currentRequest();
            if (request == null) {
                return;
            }

            String userAgent = request.getHeader("User-Agent");
            String referrer = request.getHeader("Referer");
            String country = geoCountryResolver.resolve(request.getRemoteAddr());
            String deviceType = DeviceTypeClassifier.classify(userAgent).name();

            ClickEvent event = new ClickEvent(
                    UUID.randomUUID().toString(),
                    shortCode,
                    Instant.now(),
                    userAgent,
                    referrer,
                    country,
                    deviceType
            );

            kafkaTemplate.send(KafkaConfig.CLICK_EVENTS_TOPIC, shortCode, event)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.warn("Failed to publish click event for shortCode '{}': {}", shortCode, ex.getMessage());
                        }
                    });
        } catch (RuntimeException e) {
            log.warn("Failed to build/publish click event for shortCode '{}': {}", shortCode, e.getMessage());
        }
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }
}
