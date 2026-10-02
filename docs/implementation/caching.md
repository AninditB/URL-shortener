← [Index](../00-index.md)

## Caching

Redis holds four independent keyspaces, distinguished purely by key prefix — same instance, four unrelated jobs:

| Prefix | Purpose | Written by | TTL | Invalidated by |
| --- | --- | --- | --- | --- |
| `shortcode:` | Cache-aside redirect lookup (short code → original URL) | `ShortUrlServiceImpl` | `min(expiresAt − now, 1h)`, default 1h | Explicit `DEL` on delete, disable, or expiry-detected-on-read |
| `idempotency:` | Create-request claim + replay | `IdempotencyService` | 30s while `IN_PROGRESS`, then `app.idempotency.ttl-hours` (default 24h) once completed | Natural TTL expiry only |
| `ratelimit:` | Fixed-window request counter, per identity | `RateLimiter` | `app.rate-limit.window-seconds` (default 60s) | Natural TTL expiry (new window = new key) |
| `processed-event:` | Kafka click-event dedup guard | `EventDedupService` | `app.analytics.dedup-ttl-days` | Explicit `DEL` if the DB write for that event fails (so a retry isn't silently skipped) |

This is the direct answer to "what is Redis doing": one **cache** (fast redirects), one **distributed lock/claim** (safe retries), one **rate limiter** (abuse control), one **dedup guard** (exactly-once-ish analytics) — sharing a Redis instance, never a keyspace.
