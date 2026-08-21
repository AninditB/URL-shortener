package com.aninditb.shortlink.repository;

import com.aninditb.shortlink.entity.ShortUrl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class ShortUrlRepositoryTest {

    @Autowired
    private ShortUrlRepository repository;

    @Test
    void savesAndFindsByShortCode() {
        ShortUrl entity = new ShortUrl("https://example.com/x", null);
        entity.setShortCode("java");
        repository.save(entity);

        Optional<ShortUrl> found = repository.findByShortCode("java");

        assertThat(found).isPresent();
        assertThat(found.get().getOriginalUrl()).isEqualTo("https://example.com/x");
    }

    @Test
    void existsByShortCodeReflectsSavedRows() {
        assertThat(repository.existsByShortCode("java")).isFalse();

        ShortUrl entity = new ShortUrl("https://example.com/x", null);
        entity.setShortCode("java");
        repository.saveAndFlush(entity);

        assertThat(repository.existsByShortCode("java")).isTrue();
    }

    @Test
    void enforcesUniqueShortCode() {
        ShortUrl first = new ShortUrl("https://example.com/x", null);
        first.setShortCode("java");
        repository.saveAndFlush(first);

        ShortUrl duplicate = new ShortUrl("https://example.com/y", null);
        duplicate.setShortCode("java");

        assertThatThrownBy(() -> repository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
