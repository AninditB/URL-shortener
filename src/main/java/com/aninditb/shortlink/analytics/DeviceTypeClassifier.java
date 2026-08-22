package com.aninditb.shortlink.analytics;

import java.util.Locale;

public final class DeviceTypeClassifier {

    private DeviceTypeClassifier() {
    }

    public static DeviceType classify(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return DeviceType.UNKNOWN;
        }

        String lower = userAgent.toLowerCase(Locale.ROOT);
        // Checked first: a crawler UA can also contain "Mobile" (e.g. Googlebot's
        // mobile-crawler string), and a bot signal is more specific than that.
        if (lower.contains("bot") || lower.contains("spider") || lower.contains("crawler")) {
            return DeviceType.BOT;
        }
        if (userAgent.contains("iPad") || userAgent.contains("Tablet")) {
            return DeviceType.TABLET;
        }
        if (userAgent.contains("Mobi") || userAgent.contains("Android")) {
            return DeviceType.MOBILE;
        }
        return DeviceType.DESKTOP;
    }
}
