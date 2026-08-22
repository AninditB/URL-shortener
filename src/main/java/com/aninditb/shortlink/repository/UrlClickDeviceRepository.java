package com.aninditb.shortlink.repository;

import com.aninditb.shortlink.entity.UrlClickDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface UrlClickDeviceRepository extends JpaRepository<UrlClickDevice, UrlClickDevice.Pk> {

    List<UrlClickDevice> findByShortUrlId(Long shortUrlId);

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query(value = """
            INSERT INTO url_click_device (short_url_id, device_type, click_count)
            VALUES (:shortUrlId, :deviceType, 1)
            ON CONFLICT (short_url_id, device_type)
            DO UPDATE SET click_count = url_click_device.click_count + 1
            """, nativeQuery = true)
    void incrementDevice(@Param("shortUrlId") Long shortUrlId, @Param("deviceType") String deviceType);
}
