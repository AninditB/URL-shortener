package com.aninditb.shortlink.repository;

import com.aninditb.shortlink.entity.ShortUrl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ShortUrlRepository extends JpaRepository<ShortUrl, Long> {

    Optional<ShortUrl> findByShortCode(String shortCode);

    boolean existsByShortCode(String shortCode);

    List<ShortUrl> findByOwnerIdOrderByIdDesc(Long ownerId, Pageable pageable);

    List<ShortUrl> findByOwnerIdAndIdLessThanOrderByIdDesc(Long ownerId, Long cursor, Pageable pageable);
}
