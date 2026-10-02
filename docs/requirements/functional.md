← [Index](../00-index.md)

## Functional Requirements

Grouped into six features. Each states what the feature does, its
request/response contract, and the pre/post-conditions that govern it —
formalized from the six 2.2/3.1 features of the project's IEEE-830 SRS
(`docs_I/planning/SRS-IEEE830.md`, local/untracked).

### 1. Account Management

- Register with an email + password (`POST /api/v1/auth/register`) —
  password is BCrypt-hashed, never stored in plain text. `201` on
  success, `409` if the email is already registered.
- Log in with email + password (`POST /api/v1/auth/login`) — returns a
  signed JWT (`APP_JWT_EXPIRATION_MINUTES`, default 60 minutes) on
  success (`200`), `401` on a non-matching credential pair.
- Pre-condition: registration requires the email not already be
  registered; login requires the account to already exist.
- Post-condition: a successful login yields a stateless JWT usable as a
  `Bearer` token on subsequent requests — no server-side session state
  is created.

### 2. URL Creation with Safety Validation

- Create a short URL (`POST /api/v1/urls`), optionally with a custom
  alias, an expiration timestamp, and an `Idempotency-Key` header to
  make retried creates safe. Works with or without authentication;
  authenticated creates are owned by the caller (`owner_id`), anonymous
  creates are not.
- Reject unsafe input URLs at creation time — malformed URLs, non-http(s)
  schemes, and destinations resolving to loopback/private/link-local
  addresses are all rejected with `400` (SSRF / open-redirect
  protection).
- Pre-condition: `originalUrl` passes safety validation; a supplied
  custom alias is not already taken.
- Post-condition: on success, the short code is immediately resolvable
  via redirect (below); on a validation failure, no row is created.

### 3. Redirect Resolution

- Resolve a short code to its original URL and issue a 302
  (`GET /{shortCode}`), via a cache-first (Redis) lookup falling back to
  PostgreSQL on a miss.
- `404` if the code doesn't exist or is disabled; `410 Gone` if it
  exists but has expired.
- Pre-condition: the code exists, is active (not disabled), and (if set)
  has not passed its expiration.
- Post-condition: on success, a click event is submitted for
  asynchronous publication (see Analytics below); publication is
  best-effort and never blocks or fails the redirect response itself.

### 4. URL Lifecycle Management

- Get a URL's details (`GET /api/v1/urls/{id}`).
- List the caller's own URLs, cursor-paginated (`GET /api/v1/urls?limit=&cursor=`).
- Disable a URL without deleting it (`POST /api/v1/urls/{id}/disable`) — owner or admin only.
- Re-enable a previously disabled URL (`POST /api/v1/urls/{id}/enable`) — owner or admin only; rejected with `410 Gone` if the URL has since expired.
- Delete a URL (`DELETE /api/v1/urls/{id}`) — owner or admin only.
- Pre-condition (disable/enable/delete/get): caller is authenticated and
  is the URL's owner or an admin.
- Post-condition: disable immediately stops the code from resolving
  (redirect returns `404`); enable resumes resolution (unless expired);
  delete removes the row and its short code stops resolving. Each
  invalidates any cached lookup entry for that code so the redirect path
  never serves a stale result afterward.

### 5. Abuse Protection

- Per-identity rate limiting on URL creation (`POST /api/v1/urls`): a
  fixed-window Redis counter, lower limit for anonymous callers than
  authenticated ones (`app.rate-limit.*`), returns `429` once exceeded.
- Idempotent create handling: a repeated `Idempotency-Key` within its TTL
  (`APP_IDEMPOTENCY_TTL_HOURS`, default 24 hours) replays the original
  creation result instead of creating a duplicate row, via an atomic
  reserve-then-commit claim (not a check-then-act race).
- Internal to URL creation — not separately caller-invoked.

### 6. Click Analytics

- Every successful redirect emits a click event asynchronously to Kafka
  (short code, timestamp, and — where available — user agent, referrer,
  country, device type); a failed or delayed publish never affects the
  redirect response.
- An independent consumer aggregates events per short code, deduplicating
  by event ID so redelivery doesn't double-count, and routes an event
  that repeatedly fails processing to a dead-letter topic instead of
  dropping it or blocking the consumer.
- Per-URL click analytics (`GET /api/v1/urls/{id}/analytics`): total
  clicks, a daily breakdown (last 30 days), a top-10 country breakdown,
  and a device-type breakdown (desktop/mobile/tablet/bot). Owner or
  admin only; `401`/`403`/`404` otherwise.
- Post-condition: figures reflect only events already processed by the
  consumer as of query time — a just-published click is not guaranteed
  to be reflected immediately (eventual consistency; see
  [Non-Functional Requirements](non-functional.md)).

---

Note: only `DELETE /urls/{id}`, `POST /urls/{id}/disable`, and `GET /urls`/`GET /urls/{id}` are enforced-authenticated at the Spring Security filter-chain level (`SecurityConfig`). `POST /urls`, `POST /urls/{id}/enable`, and `GET /urls/{id}/analytics` are `permitAll` there — ownership for those is enforced inside the service layer instead (`requireOwnerOrAdmin`). See [Low-Level Design](../design/low-level.md#ownership--authorization-model).

Source: [`ShortUrlController.java`](../../src/main/java/com/aninditb/shortlink/controller/ShortUrlController.java), [`AuthController.java`](../../src/main/java/com/aninditb/shortlink/controller/AuthController.java), [`RedirectController.java`](../../src/main/java/com/aninditb/shortlink/controller/RedirectController.java), [`AnalyticsService.java`](../../src/main/java/com/aninditb/shortlink/analytics/AnalyticsService.java).
