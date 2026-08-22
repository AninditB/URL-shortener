package com.aninditb.shortlink.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name = "url_click_country")
@IdClass(UrlClickCountry.Pk.class)
public class UrlClickCountry {

    @Id
    @Column(name = "short_url_id")
    private Long shortUrlId;

    @Id
    @Column(name = "country")
    private String country;

    @Column(name = "click_count", nullable = false)
    private long clickCount;

    protected UrlClickCountry() {
    }

    public UrlClickCountry(Long shortUrlId, String country, long clickCount) {
        this.shortUrlId = shortUrlId;
        this.country = country;
        this.clickCount = clickCount;
    }

    public Long getShortUrlId() {
        return shortUrlId;
    }

    public String getCountry() {
        return country;
    }

    public long getClickCount() {
        return clickCount;
    }

    public static class Pk implements Serializable {
        private Long shortUrlId;
        private String country;

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
            return Objects.equals(shortUrlId, pk.shortUrlId) && Objects.equals(country, pk.country);
        }

        @Override
        public int hashCode() {
            return Objects.hash(shortUrlId, country);
        }
    }
}
