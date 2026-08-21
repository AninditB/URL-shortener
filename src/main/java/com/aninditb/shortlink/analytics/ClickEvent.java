package com.aninditb.shortlink.analytics;

import java.time.Instant;

public record ClickEvent(
        String eventId,
        String shortCode,
        Instant timestamp,
        String userAgent,
        String referrer,
        String country,
        String deviceType
) {
}
