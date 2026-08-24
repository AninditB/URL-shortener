# ShortLink

A URL shortener built as a progressive, phase-by-phase engineering project: each phase is a complete, working system that introduces one major class of engineering problem — starting from a clean Java CRUD app and building up through caching/auth, event-driven analytics, production observability, and eventually distributed scale.

## Quickstart

Nothing is publicly hosted yet, so this runs locally. Three commands:

```bash
docker compose up -d
APP_JWT_SECRET=$(openssl rand -base64 32) mvn spring-boot:run
```

Then pick either path — both talk to the same backend and the same data:

* **REST API** — call it directly:
  ```bash
  curl -X POST http://localhost:8080/api/v1/urls \
    -H "Content-Type: application/json" \
    -d '{"originalUrl":"https://example.com/some/long/path"}'
  ```
  Full contract: [API](#api) below, or Swagger UI at `http://localhost:8080/swagger-ui.html`.
* **Webpage** — in a second terminal:
  ```bash
  cd frontend && python -m http.server 5500
  ```
  Open `http://localhost:5500`: register, create/manage short URLs, and view analytics with no code required.

Both paths are verified working end-to-end (create → disable → enable → redirect → analytics), most recently checked 2026-08-24. Full prerequisites and config reference: [Getting Started](#getting-started).

## Use Cases

* **Share a memorable link with analytics** — shorten a long campaign/marketing URL, optionally with your own custom alias, and watch click volume, top countries, and device breakdown roll in automatically as people click it.
* **Time-boxed links** — set an expiration timestamp on creation for a link that should stop working on its own (a promo, a one-time invite) without you having to remember to take it down.
* **Reversible takedown** — disable a link without losing it (e.g. while investigating a report, or pausing a campaign), then re-enable it later if you need it back.
* **Programmatic integration** — create/manage links from your own app or script via the REST API instead of the webpage; the `Idempotency-Key` header means a retried request never creates a duplicate link.
* **A worked example of the patterns it's built on** — cache-aside redirects, JWT auth, distributed rate limiting, idempotent APIs, and event-driven analytics via Kafka, all in one small, readable codebase. See [Engineering Details](#engineering-details) and the [System Design Document](docs/SYSTEM-DESIGN.md).

## Features

* Create a short URL, with optional custom alias and expiration
* Redirect a short URL to its original destination
* Disable a URL without deleting it, and re-enable it later
* Get URL details; delete a URL
* URL safety validation — rejects malformed URLs and blocks internal/private IP ranges and localhost, to prevent SSRF and open-redirect abuse
* User registration and JWT-based login; URLs you create are yours — only you (or an admin) can modify or delete them
* Cursor-paginated listing of your own URLs
* Per-URL click analytics: total clicks, daily breakdown, top countries, device-type breakdown
* Rate limiting and idempotent request handling, so retries and rapid clicks behave safely
* A webpage demo (`frontend/`) covering the full flow above with no code required

## Getting Started

### Prerequisites

* Java 21
* Maven
* Docker (for local Postgres/Redis/Kafka via Docker Compose), or locally running equivalents

### Start infrastructure

```bash
docker compose up -d
```

Starts `postgres:16`, `redis:7`, and a single-node `apache/kafka` (KRaft mode) container, matching the app's default connection settings.

### Run the backend (needed either way)

```bash
mvn spring-boot:run
```

Flyway applies schema migrations automatically on startup. The app listens on `http://localhost:8080`. `APP_JWT_SECRET` must be set to a value at least 32 bytes long — there is no default, by design.

### Option A: use it as a webpage

```bash
cd frontend
python -m http.server 5500
```

Open `http://localhost:5500`. It's hardcoded to call the API at `http://localhost:8080` (`frontend/js/api.js`).

### Option B: use it as a REST API

Call it directly once the backend is up — see [API](#api) below, or Swagger UI at `http://localhost:8080/swagger-ui.html`.

### Run tests

```bash
mvn test
```

### Configuration

The app is entirely environment-variable driven, defaulting to the Docker Compose setup above:

| Variable | Default |
| --- | --- |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/shortlink` |
| `SPRING_DATASOURCE_USERNAME` | `shortlink` |
| `SPRING_DATASOURCE_PASSWORD` | `shortlink` |
| `SPRING_DATA_REDIS_HOST` | `localhost` |
| `SPRING_DATA_REDIS_PORT` | `6379` |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` |
| `APP_BASE_URL` | `http://localhost:8080` |
| `APP_JWT_SECRET` | *(required, no default)* |
| `APP_JWT_EXPIRATION_MINUTES` | `60` |
| `APP_RATE_LIMIT_WINDOW_SECONDS` | `60` |
| `APP_RATE_LIMIT_ANONYMOUS_LIMIT` | `10` |
| `APP_RATE_LIMIT_AUTHENTICATED_LIMIT` | `100` |
| `APP_IDEMPOTENCY_TTL_HOURS` | `24` |
| `APP_CORS_ALLOWED_ORIGIN` | `http://localhost:5500` |

## API

| Method | Path | Purpose | Auth |
| --- | --- | --- | --- |
| POST | `/api/v1/auth/register` | Create a user account | None |
| POST | `/api/v1/auth/login` | Log in, receive a JWT | None |
| POST | `/api/v1/urls` | Create a short URL (accepts optional `Idempotency-Key` header) | Optional |
| GET | `/{shortCode}` | Redirect to the original URL | None |
| GET | `/api/v1/urls/{id}` | Get URL details | None |
| GET | `/api/v1/urls?limit=&cursor=` | List the caller's own URLs, paginated | Required |
| POST | `/api/v1/urls/{id}/disable` | Disable a URL without deleting it (owner/admin only) | Required if owned |
| POST | `/api/v1/urls/{id}/enable` | Re-enable a disabled URL (owner/admin only) | Required if owned |
| DELETE | `/api/v1/urls/{id}` | Delete a URL (owner/admin only) | Required if owned |
| GET | `/api/v1/urls/{id}/analytics` | Total clicks, daily/country/device breakdown | Required if owned |

Protected/ownership-gated actions use a standard `Authorization: Bearer <token>` header. Every error response shares one shape (`timestamp`, `status`, `error`, `message`, `path`), so one handler on the caller's side covers all of them. Full schemas: Swagger UI, or the [System Design Document](docs/SYSTEM-DESIGN.md#2-functional-requirements).

## License

TBD (leaning MIT)

---

## Engineering Details

The rest of this document is for anyone evaluating or extending the codebase, not just using it. For the full architecture — sequence diagrams, database ER diagram, caching strategy, code walkthroughs — see the [System Design Document](docs/SYSTEM-DESIGN.md).

### Roadmap

| Phase | Focus | Status |
| --- | --- | --- |
| 1 — Core URL Shortener | Java, REST API design, PostgreSQL | ✅ Complete |
| 2 — Production Backend | Redis caching, auth/authorization, rate limiting, idempotency | ✅ Complete |
| 3 — Scalable Platform | Kafka, event-driven click analytics | ✅ Complete |
| 4 — Production Engineering | Observability, resilience, Docker, CI/CD | ⏳ Not started |
| 5 — Enterprise Platform | Kubernetes, HA, disaster recovery, performance engineering | ⏳ Not started |

Frontend demo (plain HTML/CSS/vanilla JS, `frontend/`) is also complete.

### Status

* **Phase 1 — Complete.** Core CRUD + redirect, verified against a real PostgreSQL instance.
* **Phase 2 — Complete.** Caching, auth, rate limiting, idempotency, verified against real PostgreSQL + Redis (Testcontainers).
* **Phase 3 — Complete.** Kafka click pipeline, idempotent analytics consumer, per-URL analytics endpoint, verified against real PostgreSQL + Redis + Kafka (Testcontainers).
* **Frontend demo — Complete.** Full user flow (auth → create/list/disable/enable/delete → analytics) working end-to-end against a live backend.
* Two bugs found post-hoc via manual/real-infra testing, both fixed: a check-then-act race condition in the idempotency service (atomic Redis reservation pattern), and a missing enable/reactivate action for disabled URLs (with an expiry-detection edge case caught during that fix's own verification). See [System Design Document § Code](docs/SYSTEM-DESIGN.md#6-code).

Zero open issues/PRs; `main` is the only branch.

### Tech Stack

* Java 21, Spring Boot 3.3
* Spring Web, Spring Data JPA, Spring Data Redis, Spring Security, Spring for Apache Kafka
* PostgreSQL 16, Flyway (migrations)
* Redis 7
* Apache Kafka (KRaft mode)
* JWT (`jjwt`), BCrypt password hashing
* JUnit 5, Mockito, Testcontainers

### Architecture

```text
                 HTTP
   Client ───────────────────► Spring Boot App ───┬──► PostgreSQL
 (curl / browser)                                   ├──► Redis
                                                     └──► Kafka ──► Analytics Consumer ──► PostgreSQL
```

```text
Controller
   ↓
Service
   ↓
Repository
   ↓
PostgreSQL / Redis
```

Caching, rate limiting, idempotency, and click-event publishing are all implemented as cross-cutting concerns behind the existing service-layer methods — controllers stay unaware of Redis/Kafka's existence.

### Database Schema

```text
short_urls                    users                    url_click_daily
──────────────────            ──────────────────       ──────────────────
id                             id                        short_url_id (FK)
short_code                     email                     click_date
original_url                   password_hash             click_count
status                         role                     (PK: short_url_id, click_date)
created_at                     created_at
updated_at                                              url_click_country / url_click_device
expires_at                                              ──────────────────
owner_id  (nullable, → users.id)                        short_url_id (FK)
total_clicks                                             country / device_type
                                                          click_count
```

Schema evolves via seven versioned Flyway migrations (`src/main/resources/db/migration/`), one file per schema change.

Base62-encoded auto-increment ID for short codes: a row is inserted to obtain its database-assigned `id`, which is then Base62-encoded (`[0-9A-Za-z]`) into `short_code`. Simple and collision-free by construction, but couples code generation to a single writer — revisited in Phase 5 once multiple stateless app instances write concurrently.

### Deployment

A rough path for standing up a public instance instead of running locally:

* **Backend:** no `Dockerfile` exists yet — write one, then deploy to a Docker-friendly PaaS like Railway or Render, both of which offer managed Postgres/Redis add-ons.
* **Kafka:** the awkward piece for cheap hosting — most PaaS platforms don't include it. A serverless option like Upstash Kafka or Confluent Cloud's free tier avoids self-hosting a broker.
* **Frontend:** static, zero build step — deploys as-is to GitHub Pages, Netlify, or Vercel.
* **Secrets:** `APP_JWT_SECRET` and DB/Redis/Kafka credentials go into the platform's secret manager, never the repo.
* **Required code change:** update `APP_CORS_ALLOWED_ORIGIN` to the real frontend URL once it's known.

This gets a clickable public demo, not a production-grade deployment — no HA, observability, or CI/CD yet.

### What's Next

Phase 4 (observability, resilience, Docker, CI/CD) and Phase 5 (Kubernetes, HA, disaster recovery, performance engineering).
