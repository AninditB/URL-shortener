package com.aninditb.shortlink.repository;

import com.aninditb.shortlink.entity.UrlClickCountry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface UrlClickCountryRepository extends JpaRepository<UrlClickCountry, UrlClickCountry.Pk> {

    List<UrlClickCountry> findByShortUrlId(Long shortUrlId);

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query(value = """
            INSERT INTO url_click_country (short_url_id, country, click_count)
            VALUES (:shortUrlId, :country, 1)
            ON CONFLICT (short_url_id, country)
            DO UPDATE SET click_count = url_click_country.click_count + 1
            """, nativeQuery = true)
    void incrementCountry(@Param("shortUrlId") Long shortUrlId, @Param("country") String country);
}
