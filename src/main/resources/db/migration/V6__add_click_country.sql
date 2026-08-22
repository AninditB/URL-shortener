CREATE TABLE url_click_country (
    short_url_id BIGINT NOT NULL REFERENCES short_urls(id),
    country      VARCHAR(8) NOT NULL,
    click_count  BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (short_url_id, country)
);
