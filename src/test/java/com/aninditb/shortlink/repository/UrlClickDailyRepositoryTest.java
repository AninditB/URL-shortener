package com.aninditb.shortlink.repository;

import com.aninditb.shortlink.entity.ShortUrl;
import com.aninditb.shortlink.entity.UrlClickDaily;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * incrementDaily() itself uses Postgres-native "ON CONFLICT ... DO UPDATE" SQL, which H2's
 * PostgreSQL compatibility mode does not support (only "ON CONFLICT DO NOTHING" is - see
 * H2's compatibility docs). That upsert SQL is verified directly against real Postgres
 * instead; this test covers what H2 can portably exercise: the entity mapping and read query.
 */
@DataJpaTest
class UrlClickDailyRepositoryTest {

    @Autowired
    private ShortUrlRepository shortUrlRepository;

    @Autowired
    private UrlClickDailyRepository repository;

    @Test
    void findByShortUrlIdReturnsOnlyThatUrlsRows() {
        Long shortUrlId = saveShortUrl();
        LocalDate today = LocalDate.now();
        repository.saveAndFlush(new UrlClickDaily(shortUrlId, today, 3));
        repository.saveAndFlush(new UrlClickDaily(shortUrlId, today.minusDays(1), 1));
        Long otherShortUrlId = saveShortUrl();
        repository.saveAndFlush(new UrlClickDaily(otherShortUrlId, today, 99));

        List<UrlClickDaily> rows = repository.findByShortUrlId(shortUrlId);

        assertThat(rows).hasSize(2);
        assertThat(rows).anySatisfy(row -> {
            assertThat(row.getClickDate()).isEqualTo(today);
            assertThat(row.getClickCount()).isEqualTo(3);
        });
        assertThat(rows).anySatisfy(row -> {
            assertThat(row.getClickDate()).isEqualTo(today.minusDays(1));
            assertThat(row.getClickCount()).isEqualTo(1);
        });
    }

    private Long saveShortUrl() {
        ShortUrl entity = new ShortUrl("https://example.com/x", null);
        entity.setShortCode("code-" + System.nanoTime());
        return shortUrlRepository.saveAndFlush(entity).getId();
    }
}
