package com.aninditb.shortlink.repository;

import com.aninditb.shortlink.entity.UrlClickDaily;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

public interface UrlClickDailyRepository extends JpaRepository<UrlClickDaily, UrlClickDaily.Pk> {

    List<UrlClickDaily> findByShortUrlId(Long shortUrlId);

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query(value = """
            INSERT INTO url_click_daily (short_url_id, click_date, click_count)
            VALUES (:shortUrlId, :clickDate, 1)
            ON CONFLICT (short_url_id, click_date)
            DO UPDATE SET click_count = url_click_daily.click_count + 1
            """, nativeQuery = true)
    void incrementDaily(@Param("shortUrlId") Long shortUrlId, @Param("clickDate") LocalDate clickDate);
}
