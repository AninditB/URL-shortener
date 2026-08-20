CREATE TABLE short_urls (
    id           BIGSERIAL PRIMARY KEY,
    short_code   VARCHAR(32)  NOT NULL,
    original_url TEXT         NOT NULL,
    status       VARCHAR(16)  NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL,
    expires_at   TIMESTAMPTZ
);

CREATE UNIQUE INDEX uk_short_urls_short_code ON short_urls (short_code);
