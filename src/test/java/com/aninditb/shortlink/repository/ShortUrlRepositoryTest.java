package com.aninditb.shortlink.repository;

import com.aninditb.shortlink.entity.ShortUrl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
    void incrementTotalClicksAccumulates() {
        ShortUrl entity = new ShortUrl("https://example.com/x", null);
        entity.setShortCode("java");
        Long id = repository.saveAndFlush(entity).getId();

        repository.incrementTotalClicks(id);
        repository.incrementTotalClicks(id);

        assertThat(repository.findById(id).orElseThrow().getTotalClicks()).isEqualTo(2);
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

    @Test
    void keysetPaginationWalksAllRowsWithoutRepeatOrSkip() {
        Long ownerId = 7L;
        Set<Long> expectedIds = new HashSet<>();
        for (int i = 0; i < 5; i++) {
            ShortUrl entity = new ShortUrl("https://example.com/" + i, null);
            entity.setShortCode("code" + i);
            entity.setOwnerId(ownerId);
            ShortUrl saved = repository.saveAndFlush(entity);
            expectedIds.add(saved.getId());
        }
        // A different owner's row must never appear in owner 7's pages.
        ShortUrl other = new ShortUrl("https://example.com/other", null);
        other.setShortCode("other-owner-code");
        other.setOwnerId(99L);
        repository.saveAndFlush(other);

        List<Long> collected = new ArrayList<>();
        Long cursor = null;
        int pageCount = 0;
        do {
            List<ShortUrl> page = cursor == null
                    ? repository.findByOwnerIdOrderByIdDesc(ownerId, PageRequest.of(0, 2))
                    : repository.findByOwnerIdAndIdLessThanOrderByIdDesc(ownerId, cursor, PageRequest.of(0, 2));
            page.forEach(u -> collected.add(u.getId()));
            cursor = page.isEmpty() || page.size() < 2 ? null : page.get(page.size() - 1).getId();
            pageCount++;
        } while (cursor != null && pageCount < 10);

        assertThat(collected).hasSize(5);
        assertThat(new HashSet<>(collected)).isEqualTo(expectedIds);
        // newest-first: each page's ids must be strictly descending across the whole walk
        assertThat(collected).isSortedAccordingTo((a, b) -> b.compareTo(a));
    }
}
