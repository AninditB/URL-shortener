package com.aninditb.shortlink.repository;

import com.aninditb.shortlink.entity.ShortUrl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface ShortUrlRepository extends JpaRepository<ShortUrl, Long> {

    Optional<ShortUrl> findByShortCode(String shortCode);

    boolean existsByShortCode(String shortCode);

    List<ShortUrl> findByOwnerIdOrderByIdDesc(Long ownerId, Pageable pageable);

    List<ShortUrl> findByOwnerIdAndIdLessThanOrderByIdDesc(Long ownerId, Long cursor, Pageable pageable);

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE ShortUrl s SET s.totalClicks = s.totalClicks + 1 WHERE s.id = :id")
    void incrementTotalClicks(@Param("id") Long id);
}
