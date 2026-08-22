package com.aninditb.shortlink.dto;

import java.util.Map;

public record AnalyticsResponse(
        long totalClicks,
        Map<String, Long> clicksByDay,
        Map<String, Long> topCountries,
        Map<String, Long> devices
) {
}
