package com.aninditb.shortlink.repository;

import com.aninditb.shortlink.entity.ShortUrl;
import com.aninditb.shortlink.entity.UrlClickDevice;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * incrementDevice() itself uses Postgres-native "ON CONFLICT ... DO UPDATE" SQL, unsupported
 * by H2's PostgreSQL compatibility mode (see UrlClickDailyRepositoryTest) - verified directly
 * against real Postgres instead. This test covers the entity mapping and read query.
 */
@DataJpaTest
class UrlClickDeviceRepositoryTest {

    @Autowired
    private ShortUrlRepository shortUrlRepository;

    @Autowired
    private UrlClickDeviceRepository repository;

    @Test
    void findByShortUrlIdReturnsOnlyThatUrlsRows() {
        Long shortUrlId = saveShortUrl();
        repository.saveAndFlush(new UrlClickDevice(shortUrlId, "DESKTOP", 2));
        repository.saveAndFlush(new UrlClickDevice(shortUrlId, "MOBILE", 1));
        Long otherShortUrlId = saveShortUrl();
        repository.saveAndFlush(new UrlClickDevice(otherShortUrlId, "DESKTOP", 99));

        List<UrlClickDevice> rows = repository.findByShortUrlId(shortUrlId);

        assertThat(rows).hasSize(2);
        assertThat(rows).anySatisfy(row -> {
            assertThat(row.getDeviceType()).isEqualTo("DESKTOP");
            assertThat(row.getClickCount()).isEqualTo(2);
        });
        assertThat(rows).anySatisfy(row -> {
            assertThat(row.getDeviceType()).isEqualTo("MOBILE");
            assertThat(row.getClickCount()).isEqualTo(1);
        });
    }

    private Long saveShortUrl() {
        ShortUrl entity = new ShortUrl("https://example.com/x", null);
        entity.setShortCode("code-" + System.nanoTime());
        return shortUrlRepository.saveAndFlush(entity).getId();
    }
}
