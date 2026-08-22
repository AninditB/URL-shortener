CREATE TABLE url_click_device (
    short_url_id BIGINT NOT NULL REFERENCES short_urls(id),
    device_type  VARCHAR(16) NOT NULL,
    click_count  BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (short_url_id, device_type)
);
