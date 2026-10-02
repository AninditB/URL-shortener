← [Index](../00-index.md)

## Non-Functional Requirements

Grouped to match the project's IEEE-830 SRS (`docs_I/planning/SRS-IEEE830.md`,
local/untracked) §3.2/§3.3. No numeric target is stated here unless it's
an actual configured/enforced value today — the SRS marks unmeasured
targets (e.g. p95 latency, uptime %, concurrent-user capacity) as
`[TBD]`, and this document leaves them equally unspecified rather than
inventing a number; see [Not Yet Built](../not-yet-built.md).

### Performance

- The redirect path is cache-aside (Redis first, Postgres fallback) and
  never waits on analytics; click events are published to Kafka
  asynchronously and swallow their own errors, so a Kafka hiccup can
  never fail or delay a redirect.
- No p95/p99 latency target or concurrent-request capacity has been
  measured or committed to yet (`[TBD]` in the SRS) — recommend
  establishing one via load testing before any production deployment.

### Reliability

- URL creation is idempotent under retry (Redis-backed atomic claim, not
  a check-then-act race).
- The Kafka consumer deduplicates by event ID so redelivery doesn't
  double-count a click, and un-marks the dedup key on a failed DB write
  so the event can actually be retried instead of being silently
  swallowed; a message that still fails after bounded retries is routed
  to a dead-letter topic rather than dropped.
- **Consistency** — analytics are eventually consistent by design: the
  redirect returns before the click is durably counted. This is a
  deliberate latency-over-consistency trade-off, not an oversight.
- No uptime SLA or redundancy/failover strategy is defined yet — see
  Availability below.

### Security

- JWT-based auth (signed, configurable expiration via
  `APP_JWT_EXPIRATION_MINUTES`, no built-in default signing secret — the
  app refuses to start without `APP_JWT_SECRET` configured).
- Per-user ownership checks fail closed: an unauthenticated caller gets
  `401`, an authenticated non-owner/non-admin gets `403` — access is
  never assumed, only explicitly granted.
- SSRF-safe URL validation on every create (rejects non-http(s) schemes
  and loopback/private/link-local destinations); open-redirect
  protection follows from the same check.
- BCrypt password hashing; passwords are never persisted or logged in
  plaintext.
- CORS locked to a configured origin (`APP_CORS_ALLOWED_ORIGIN`).
- TLS termination is not handled by the application itself — a
  production deployment is expected to terminate TLS at its
  ingress/load balancer (not yet a validated or enforced requirement).

### Availability

- No failover or redundancy for any component today — a single instance
  each of the app process, Redis, Postgres, and the Kafka broker; a
  Redis or Postgres outage currently takes the corresponding feature
  down rather than degrading gracefully.
- The redirect path and URL-management endpoints are architecturally
  independent of the Kafka-based analytics pipeline's availability — an
  analytics-pipeline outage does not take down redirect/create/manage
  functionality, per the Performance/Consistency trade-off above.
- No scheduled-maintenance-window policy or uptime SLA is defined yet.

### Usability

- Every error response across all endpoints shares one response shape
  (`timestamp`, `status`, `error`, `message`, `path`), so a single
  client-side error handler covers every endpoint.
- The bundled frontend demo (`frontend/`) covers the full user flow
  (register → create/list/disable/enable/delete → analytics) with no
  code required.
- No accessibility standard (e.g. WCAG) has been evaluated against the
  frontend demo yet.

### Flexibility

- The application is entirely environment-variable driven for
  configuration (data source, Redis, Kafka, base URL, JWT secret/
  expiration, rate-limit thresholds, idempotency TTL, CORS origin) — no
  deployment-specific value requires a code change or rebuild.
- Analytics/Kafka logic is isolated from the core URL service layer, so a
  future change to the event pipeline or analytics storage engine
  doesn't require touching create/redirect business logic.
- The Kafka consumer-group design supports adding consumer instances to
  scale partition consumption without redesign, even though a single
  instance is sufficient today.

### Scalability

- Currently a single instance of each service (one app process, one
  Redis, one Postgres, one Kafka broker). No clustering or horizontal
  scaling exists yet — see [Not Yet Built](../not-yet-built.md).
