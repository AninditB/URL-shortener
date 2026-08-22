CREATE TABLE url_click_daily (
    short_url_id BIGINT NOT NULL REFERENCES short_urls(id),
    click_date   DATE   NOT NULL,
    click_count  BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (short_url_id, click_date)
);
