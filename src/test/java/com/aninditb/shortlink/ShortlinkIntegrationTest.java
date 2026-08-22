package com.aninditb.shortlink;

import com.aninditb.shortlink.dto.AnalyticsResponse;
import com.aninditb.shortlink.dto.PagedUrlResponse;
import com.aninditb.shortlink.dto.ShortUrlResponse;
import com.aninditb.shortlink.dto.TokenResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * One broad happy-path test proving Postgres + Redis + the app wire together
 * against real containers (Flyway migrations included) - additive to, not a
 * replacement for, the existing H2/mock-based test suites.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ShortlinkIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("shortlink")
            .withUsername("shortlink")
            .withPassword("shortlink");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7"))
            .withExposedPorts(6379);

    // confluentinc/cp-kafka, not apache/kafka (used by docker-compose.yml for local dev) - this
    // testcontainers version's KafkaContainer only knows how to patch advertised.listeners with
    // the dynamically-assigned host port for the Confluent image's startup script.
    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void registerLoginCreateRedirectDisableHappyPath() throws Exception {
        String email = "integration-" + System.nanoTime() + "@example.com";

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content("{\"email\":\"" + email + "\",\"password\":\"integration-pw\"}"))
                .andExpect(status().isCreated());

        String loginJson = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("{\"email\":\"" + email + "\",\"password\":\"integration-pw\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readValue(loginJson, TokenResponse.class).token();

        String createJson = mockMvc.perform(post("/api/v1/urls")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "integration-key")
                        .contentType("application/json")
                        .content("{\"originalUrl\":\"https://example.com/integration-test\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String shortCode = objectMapper.readValue(createJson, ShortUrlResponse.class).shortCode();

        mockMvc.perform(get("/" + shortCode))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/integration-test"));
        assertThat(redisTemplate.hasKey("shortcode:" + shortCode)).isTrue();

        mockMvc.perform(get("/" + shortCode))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/integration-test"));

        String listJson = mockMvc.perform(get("/api/v1/urls").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readValue(listJson, PagedUrlResponse.class).items().get(0).id();

        mockMvc.perform(post("/api/v1/urls/" + id + "/disable")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/" + shortCode))
                .andExpect(status().isGone());
    }

    @Test
    void redirectPublishesClickEventConsumedIntoAnalytics() throws Exception {
        String email = "integration-analytics-" + System.nanoTime() + "@example.com";

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content("{\"email\":\"" + email + "\",\"password\":\"integration-pw\"}"))
                .andExpect(status().isCreated());

        String loginJson = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("{\"email\":\"" + email + "\",\"password\":\"integration-pw\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readValue(loginJson, TokenResponse.class).token();

        String createJson = mockMvc.perform(post("/api/v1/urls")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"originalUrl\":\"https://example.com/analytics-integration-test\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String shortCode = objectMapper.readValue(createJson, ShortUrlResponse.class).shortCode();

        String listJson = mockMvc.perform(get("/api/v1/urls").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readValue(listJson, PagedUrlResponse.class).items().get(0).id();

        mockMvc.perform(get("/" + shortCode)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0"))
                .andExpect(status().isFound());

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            String analyticsJson = mockMvc.perform(get("/api/v1/urls/" + id + "/analytics")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            AnalyticsResponse analytics = objectMapper.readValue(analyticsJson, AnalyticsResponse.class);
            assertThat(analytics.totalClicks()).isEqualTo(1);
            assertThat(analytics.devices()).containsEntry("DESKTOP", 1L);
            // No real GeoIP database is configured in tests (see src/test/resources/application.yml);
            // GeoCountryResolver degrades to UNKNOWN rather than failing the publish (NFR-1).
            assertThat(analytics.topCountries()).containsEntry("UNKNOWN", 1L);
        });
    }
}
