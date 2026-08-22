package com.aninditb.shortlink.analytics;

import com.aninditb.shortlink.entity.ShortUrl;
import com.aninditb.shortlink.repository.ShortUrlRepository;
import com.aninditb.shortlink.repository.UrlClickCountryRepository;
import com.aninditb.shortlink.repository.UrlClickDailyRepository;
import com.aninditb.shortlink.repository.UrlClickDeviceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Uses real Postgres (Testcontainers), not H2: the consumer's aggregate writes go through the
 * same native "ON CONFLICT ... DO UPDATE" SQL that H2's PostgreSQL compatibility mode doesn't
 * support (see UrlClickDailyRepositoryTest) - and since all four writes share one @Transactional
 * method, even the H2-portable total_clicks increment would roll back with them on H2.
 */
@SpringBootTest
@Testcontainers
@EmbeddedKafka(partitions = 1, topics = "url.clicks.v1")
@TestPropertySource(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
class AnalyticsClickConsumerTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("shortlink")
            .withUsername("shortlink")
            .withPassword("shortlink");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private KafkaTemplate<String, ClickEvent> kafkaTemplate;

    @Autowired
    private ShortUrlRepository shortUrlRepository;

    @Autowired
    private UrlClickDailyRepository dailyRepository;

    @Autowired
    private UrlClickCountryRepository countryRepository;

    @Autowired
    private UrlClickDeviceRepository deviceRepository;

    @Test
    void consumingAClickEventUpdatesAllAggregates() {
        Long shortUrlId = saveShortUrl("consumer-test");
        ClickEvent event = new ClickEvent(
                UUID.randomUUID().toString(), shortCodeFor(shortUrlId), Instant.now(),
                "some-agent", "https://ref.example.com", "US", "DESKTOP"
        );

        kafkaTemplate.send("url.clicks.v1", event.shortCode(), event);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(shortUrlRepository.findById(shortUrlId).orElseThrow().getTotalClicks()).isEqualTo(1);
            assertThat(dailyRepository.findByShortUrlId(shortUrlId)).hasSize(1);
            assertThat(countryRepository.findByShortUrlId(shortUrlId)).hasSize(1);
            assertThat(deviceRepository.findByShortUrlId(shortUrlId)).hasSize(1);
        });
    }

    @Test
    void redeliveredEventIsNotDoubleCounted() {
        Long shortUrlId = saveShortUrl("dedup-test");
        String eventId = UUID.randomUUID().toString();
        ClickEvent event = new ClickEvent(
                eventId, shortCodeFor(shortUrlId), Instant.now(), "ua", null, "US", "DESKTOP"
        );

        kafkaTemplate.send("url.clicks.v1", event.shortCode(), event);
        kafkaTemplate.send("url.clicks.v1", event.shortCode(), event);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(shortUrlRepository.findById(shortUrlId).orElseThrow().getTotalClicks()).isEqualTo(1));
        // Same partition key => Kafka preserves order, so by the time the first assertion
        // passes the redelivered duplicate has already been through the consumer too.
        assertThat(shortUrlRepository.findById(shortUrlId).orElseThrow().getTotalClicks()).isEqualTo(1);
    }

    private Long saveShortUrl(String prefix) {
        ShortUrl entity = new ShortUrl("https://example.com/x", null);
        entity.setShortCode(prefix + "-" + System.nanoTime());
        return shortUrlRepository.saveAndFlush(entity).getId();
    }

    private String shortCodeFor(Long id) {
        return shortUrlRepository.findById(id).orElseThrow().getShortCode();
    }
}
