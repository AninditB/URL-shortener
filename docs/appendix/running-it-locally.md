← [Index](../00-index.md)

## Appendix: Running It Locally

```bash
# 1. Start Postgres, Redis, and Kafka
docker compose up -d

# 2. Run the backend (both env vars are required, no defaults)
APP_JWT_SECRET=<32+ byte secret> \
APP_GEOIP_DATABASE_PATH=<path to a GeoLite2-Country.mmdb file> \
mvn spring-boot:run

# 3. Serve the frontend separately (from inside frontend/)
python -m http.server 5500
```

The backend listens on `http://localhost:8080`; the frontend expects it there by default (`frontend/js/api.js`'s `API_BASE` is hardcoded, not env-driven). Swagger UI is available at `http://localhost:8080/swagger-ui.html` once the backend is running.

Two required settings have no default and the app will fail to start (or fail on first redirect) without them:
- `APP_JWT_SECRET` — must be at least 32 bytes; used to HMAC-sign JWTs (`JwtService`).
- `APP_GEOIP_DATABASE_PATH` — path to a MaxMind GeoLite2 Country `.mmdb` file. Because `GeoIpConfig`'s `DatabaseReader` bean is `@Lazy`, a missing/bad path won't fail startup — it fails the first time a redirect tries to resolve a click's country, and `GeoCountryResolver` catches that and returns `"UNKNOWN"` rather than breaking the redirect (see [High-Level Design](../design/high-level.md)). A GeoLite2 database requires a free MaxMind account to download.

All other `app.*` settings (rate limits, idempotency TTL, CORS origin, analytics dedup TTL) have working defaults in `application.yml` and don't need to be set locally.
