package com.aninditb.shortlink.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(name = "url_click_daily")
@IdClass(UrlClickDaily.Pk.class)
public class UrlClickDaily {

    @Id
    @Column(name = "short_url_id")
    private Long shortUrlId;

    @Id
    @Column(name = "click_date")
    private LocalDate clickDate;

    @Column(name = "click_count", nullable = false)
    private long clickCount;

    protected UrlClickDaily() {
    }

    public UrlClickDaily(Long shortUrlId, LocalDate clickDate, long clickCount) {
        this.shortUrlId = shortUrlId;
        this.clickDate = clickDate;
        this.clickCount = clickCount;
    }

    public Long getShortUrlId() {
        return shortUrlId;
    }

    public LocalDate getClickDate() {
        return clickDate;
    }

    public long getClickCount() {
        return clickCount;
    }

    public static class Pk implements Serializable {
        private Long shortUrlId;
        private LocalDate clickDate;

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
            return Objects.equals(shortUrlId, pk.shortUrlId) && Objects.equals(clickDate, pk.clickDate);
        }

        @Override
        public int hashCode() {
            return Objects.hash(shortUrlId, clickDate);
        }
    }
}
