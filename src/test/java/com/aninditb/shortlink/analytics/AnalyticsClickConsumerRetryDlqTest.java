package com.aninditb.shortlink.analytics;

import com.aninditb.shortlink.config.KafkaConfig;
import com.aninditb.shortlink.entity.ShortUrl;
import com.aninditb.shortlink.repository.ShortUrlRepository;
import com.aninditb.shortlink.repository.UrlClickCountryRepository;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * Forces a real, downstream Postgres failure (a country value that violates the
 * url_click_country.country VARCHAR(8) constraint) - not a mocked dependency - so the failure
 * happens *after* EventDedupService has already marked the event processed in real Redis,
 * exactly reproducing a bug found via manual real-infra verification: the dedup key (Redis,
 * not covered by the JPA @Transactional rollback) survived the failed transaction, so a plain
 * "throw and let Kafka retry" consumer would see the retried event as already-processed and
 * silently skip it forever, never reaching the DLQ. AnalyticsClickConsumer now unmarks the
 * event on failure specifically so retries here are genuine, not silently swallowed.
 */
@SpringBootTest
@Testcontainers
@EmbeddedKafka(partitions = 1, topics = {KafkaConfig.CLICK_EVENTS_TOPIC, KafkaConfig.CLICK_EVENTS_DLQ_TOPIC})
@TestPropertySource(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
class AnalyticsClickConsumerRetryDlqTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("shortlink")
            .withUsername("shortlink")
            .withPassword("shortlink");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private KafkaTemplate<String, ClickEvent> kafkaTemplate;

    @Autowired
    private ShortUrlRepository shortUrlRepository;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @SpyBean
    private UrlClickCountryRepository countryRepository;

    @Test
    void poisonMessageIsRetriedThenDeadLetteredAndConsumptionContinues() {
        Long shortUrlId = saveShortUrl("retry-dlq-test");
        String shortCode = shortUrlRepository.findById(shortUrlId).orElseThrow().getShortCode();

        String poisonEventId = UUID.randomUUID().toString();
        ClickEvent poison = new ClickEvent(
                poisonEventId, shortCode, Instant.now(), "ua", null, "TOO-LONG-COUNTRY", "DESKTOP");

        String goodEventId = UUID.randomUUID().toString();
        ClickEvent good = new ClickEvent(goodEventId, shortCode, Instant.now(), "ua", null, "US", "DESKTOP");

        kafkaTemplate.send(KafkaConfig.CLICK_EVENTS_TOPIC, poison.shortCode(), poison);
        kafkaTemplate.send(KafkaConfig.CLICK_EVENTS_TOPIC, good.shortCode(), good);

        // 3 total delivery attempts for the poison message before it's dead-lettered.
        verify(countryRepository, timeout(15000).times(3))
                .incrementCountry(eq(shortUrlId), eq("TOO-LONG-COUNTRY"));

        assertThat(consumeOneDlqRecordValue()).contains(poisonEventId);

        // Offset advanced past the poison message: the good one still updates aggregates.
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(shortUrlRepository.findById(shortUrlId).orElseThrow().getTotalClicks()).isEqualTo(1));
    }

    private String consumeOneDlqRecordValue() {
        Map<String, Object> props = KafkaTestUtils.consumerProps("dlq-test-group", "true", embeddedKafkaBroker);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(KafkaConfig.CLICK_EVENTS_DLQ_TOPIC));
            ConsumerRecords<String, String> records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(15));
            assertThat(records.count()).isGreaterThanOrEqualTo(1);
            return records.iterator().next().value();
        }
    }

    private Long saveShortUrl(String prefix) {
        ShortUrl entity = new ShortUrl("https://example.com/x", null);
        entity.setShortCode(prefix + "-" + System.nanoTime());
        return shortUrlRepository.saveAndFlush(entity).getId();
    }
}
