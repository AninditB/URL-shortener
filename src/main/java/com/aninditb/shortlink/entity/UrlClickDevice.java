package com.aninditb.shortlink.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name = "url_click_device")
@IdClass(UrlClickDevice.Pk.class)
public class UrlClickDevice {

    @Id
    @Column(name = "short_url_id")
    private Long shortUrlId;

    @Id
    @Column(name = "device_type")
    private String deviceType;

    @Column(name = "click_count", nullable = false)
    private long clickCount;

    protected UrlClickDevice() {
    }

    public UrlClickDevice(Long shortUrlId, String deviceType, long clickCount) {
        this.shortUrlId = shortUrlId;
        this.deviceType = deviceType;
        this.clickCount = clickCount;
    }

    public Long getShortUrlId() {
        return shortUrlId;
    }

    public String getDeviceType() {
        return deviceType;
    }

    public long getClickCount() {
        return clickCount;
    }

    public static class Pk implements Serializable {
        private Long shortUrlId;
        private String deviceType;

        public Pk() {
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Pk pk)) {
                return false;
            }
            return Objects.equals(shortUrlId, pk.shortUrlId) && Objects.equals(deviceType, pk.deviceType);
        }

        @Override
        public int hashCode() {
            return Objects.hash(shortUrlId, deviceType);
        }
    }
}
