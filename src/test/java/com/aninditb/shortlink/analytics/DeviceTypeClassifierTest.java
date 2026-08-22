package com.aninditb.shortlink.analytics;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeviceTypeClassifierTest {

    private static final String DESKTOP_CHROME =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36";
    private static final String ANDROID_CHROME_PHONE =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36";
    private static final String IPHONE_SAFARI =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1";
    private static final String IPAD_SAFARI =
            "Mozilla/5.0 (iPad; CPU OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1";
    private static final String GOOGLEBOT =
            "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)";
    private static final String GOOGLEBOT_MOBILE_CRAWLER =
            "Mozilla/5.0 (Linux; Android 6.0.1; Nexus 5X Build/MMB29P) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/41.0.2272.96 Mobile Safari/537.36 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)";
    private static final String BINGBOT =
            "Mozilla/5.0 (compatible; bingbot/2.0; +http://www.bing.com/bingbot.htm)";
    private static final String GENERIC_CRAWLER = "Mozilla/5.0 (compatible; SomeCrawler/1.0)";

    @Test
    void classifiesDesktopUserAgentAsDesktop() {
        assertThat(DeviceTypeClassifier.classify(DESKTOP_CHROME)).isEqualTo(DeviceType.DESKTOP);
    }

    @Test
    void classifiesAndroidPhoneUserAgentAsMobile() {
        assertThat(DeviceTypeClassifier.classify(ANDROID_CHROME_PHONE)).isEqualTo(DeviceType.MOBILE);
    }

    @Test
    void classifiesIphoneUserAgentAsMobile() {
        assertThat(DeviceTypeClassifier.classify(IPHONE_SAFARI)).isEqualTo(DeviceType.MOBILE);
    }

    @Test
    void classifiesIpadUserAgentAsTablet() {
        assertThat(DeviceTypeClassifier.classify(IPAD_SAFARI)).isEqualTo(DeviceType.TABLET);
    }

    @Test
    void classifiesGooglebotAsBot() {
        assertThat(DeviceTypeClassifier.classify(GOOGLEBOT)).isEqualTo(DeviceType.BOT);
    }

    @Test
    void classifiesGooglebotMobileCrawlerAsBotNotMobile() {
        assertThat(DeviceTypeClassifier.classify(GOOGLEBOT_MOBILE_CRAWLER)).isEqualTo(DeviceType.BOT);
    }

    @Test
    void classifiesBingbotAsBot() {
        assertThat(DeviceTypeClassifier.classify(BINGBOT)).isEqualTo(DeviceType.BOT);
    }

    @Test
    void classifiesGenericCrawlerSubstringAsBot() {
        assertThat(DeviceTypeClassifier.classify(GENERIC_CRAWLER)).isEqualTo(DeviceType.BOT);
    }

    @Test
    void classifiesNullUserAgentAsUnknown() {
        assertThat(DeviceTypeClassifier.classify(null)).isEqualTo(DeviceType.UNKNOWN);
    }

    @Test
    void classifiesBlankUserAgentAsUnknown() {
        assertThat(DeviceTypeClassifier.classify("   ")).isEqualTo(DeviceType.UNKNOWN);
    }
}
