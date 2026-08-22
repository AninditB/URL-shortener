ALTER TABLE short_urls ADD COLUMN total_clicks BIGINT NOT NULL DEFAULT 0;

CREATE TABLE url_click_daily (
    short_url_id BIGINT NOT NULL REFERENCES short_urls(id),
    click_date   DATE   NOT NULL,
    click_count  BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (short_url_id, click_date)
);

CREATE TABLE url_click_country (
    short_url_id BIGINT NOT NULL REFERENCES short_urls(id),
    country      VARCHAR(8) NOT NULL,
    click_count  BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (short_url_id, country)
);

CREATE TABLE url_click_device (
    short_url_id BIGINT NOT NULL REFERENCES short_urls(id),
    device_type  VARCHAR(16) NOT NULL,
    click_count  BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (short_url_id, device_type)
);
