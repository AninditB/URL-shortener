# ShortLink

A URL shortener built as a progressive, phase-by-phase engineering project: each phase is a complete, working system that introduces one major class of engineering problem — starting from a clean Java CRUD app and building up through caching/auth, event-driven analytics, production observability, and eventually distributed scale.

| Phase | Focus | Status |
| --- | --- | --- |
| 1 — Core URL Shortener | Java, REST API design, PostgreSQL | ✅ Complete |
| 2 — Production Backend | Redis caching, auth/authorization, rate limiting, idempotency | ✅ Complete |
| 3 — Scalable Platform | Kafka, event-driven click analytics | ✅ Complete |
| 4 — Production Engineering | Observability, resilience, Docker, CI/CD | ⏳ Not started |
| 5 — Enterprise Platform | Kubernetes, HA, disaster recovery, performance engineering | ⏳ Not started |

A frontend demo page (plain HTML/CSS/vanilla JS, `frontend/`) is also complete, covering the full user flow against the API above.

## Documentation

* [System Design Document](docs/SYSTEM-DESIGN.md) — architecture, requirements, sequence diagrams, database, and caching strategy for the system as it stands today

## Features

**Core (Phase 1)**
* Create a short URL, with optional custom alias and expiration
* Redirect a short URL to its original destination
* Get URL details; delete a URL
* URL safety validation — rejects malformed URLs and blocks internal/private IP ranges and localhost, to prevent SSRF and open-redirect abuse

**Production backend (Phase 2)**
* Redis cache-aside redirects
* User registration and JWT-based login
* URL ownership — delete/disable restricted to the owner or an admin
* Disable a URL without deleting it
* Cursor-paginated listing of a caller's own URLs
* Redis-backed rate limiting on URL creation (higher limit for authenticated callers)
* Idempotency-Key support on URL creation
* Testcontainers-backed integration test against real Postgres + Redis

**Scalable platform (Phase 3)**
* Kafka infrastructure for an event-driven click pipeline (topic + dead-letter topic, typed producer/consumer wiring)
* Click-event publishing on every redirect — fire-and-forget, so a Kafka failure never breaks the redirect itself
* An idempotent analytics consumer (Redis-backed event dedup, so Kafka's at-least-once delivery can't double-count a click)
* Per-URL analytics endpoint: total clicks, daily breakdown, top countries, device-type breakdown

**Frontend demo (`frontend/`)**
* Plain HTML/CSS/vanilla JS, no build tooling — served independently and calls the API cross-origin (CORS)
* Register/login, create a short URL (custom alias, expiry, idempotency-key-backed double-submit protection), paginated list of your own URLs, disable/delete, and an analytics modal per link

## Tech Stack

* Java 21, Spring Boot 3.3
* Spring Web, Spring Data JPA, Spring Data Redis, Spring Security, Spring for Apache Kafka
* PostgreSQL 16, Flyway (migrations)
* Redis 7
* Apache Kafka (KRaft mode)
* JWT (`jjwt`), BCrypt password hashing
* JUnit 5, Mockito, Testcontainers

## Architecture

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

Caching, rate limiting, idempotency, and click-event publishing are all implemented as cross-cutting concerns behind the existing service-layer methods — controllers stay unaware of Redis/Kafka's existence. See the [System Design Document](docs/SYSTEM-DESIGN.md) for sequence diagrams of each flow.

## API

| Method | Path | Purpose | Auth |
| --- | --- | --- | --- |
| POST | `/api/v1/urls` | Create a short URL (accepts optional `Idempotency-Key` header) | Optional |
| GET | `/{shortCode}` | Redirect to the original URL | None |
| GET | `/api/v1/urls/{id}` | Get URL details | None |
| GET | `/api/v1/urls?limit=&cursor=` | List the caller's own URLs, paginated | Required |
| DELETE | `/api/v1/urls/{id}` | Delete a URL (owner/admin only) | Required if owned |
| POST | `/api/v1/urls/{id}/disable` | Disable a URL without deleting it (owner/admin only) | Required if owned |
| POST | `/api/v1/auth/register` | Create a user account | None |
| POST | `/api/v1/auth/login` | Log in, receive a JWT | None |
| GET | `/api/v1/urls/{id}/analytics` | Total clicks, daily/country/device breakdown | Required if owned |

Protected/ownership-gated actions use a standard `Authorization: Bearer <token>` header. Full schemas are documented via OpenAPI/Swagger (see below).

## Database Schema

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

Schema evolves via seven versioned Flyway migrations (`src/main/resources/db/migration/`), one file per schema change. Full ER diagram in the [System Design Document](docs/SYSTEM-DESIGN.md#7-database).

### Short-code generation

Base62-encoded auto-increment ID: a row is inserted to obtain its database-assigned `id`, which is then Base62-encoded (`[0-9A-Za-z]`) into `short_code`. Simple and collision-free by construction, but couples code generation to a single writer — revisited in Phase 5 once multiple stateless app instances write concurrently (e.g. Snowflake IDs or a Redis-backed atomic counter).

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

### Run locally

```bash
mvn spring-boot:run
```

Flyway applies schema migrations automatically on startup. The app listens on `http://localhost:8080`. `APP_JWT_SECRET` must be set to a value at least 32 bytes long — there is no default, by design.

### Run the frontend demo

```bash
cd frontend
python -m http.server 5500
```

Open `http://localhost:5500`. It's hardcoded to call the API at `http://localhost:8080` (`frontend/js/api.js`).

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

### API Docs

Once running, Swagger UI is available at `http://localhost:8080/swagger-ui.html`.

## Status

* **Phase 1 — Complete.** Core CRUD + redirect, verified against a real PostgreSQL instance.
* **Phase 2 — Complete.** Caching, auth, rate limiting, idempotency, verified against real PostgreSQL + Redis (Testcontainers).
* **Phase 3 — Complete.** Kafka click pipeline, idempotent analytics consumer, per-URL analytics endpoint, verified against real PostgreSQL + Redis + Kafka (Testcontainers).
* **Frontend demo — Complete.** Full user flow (auth → create/list/disable/delete → analytics) working end-to-end against a live backend.
* A check-then-act race condition found in the idempotency service during frontend testing was fixed with an atomic Redis reservation pattern — see [System Design Document § Code](docs/SYSTEM-DESIGN.md#6-code).

Zero open issues/PRs; `main` is the only branch.

## Deployment

Nothing is publicly hosted yet — running it means running it locally (see Getting Started above). A rough path for standing up a public demo:

* **Backend:** no `Dockerfile` exists yet — write one, then deploy to a Docker-friendly PaaS like Railway or Render, both of which offer managed Postgres/Redis add-ons.
* **Kafka:** the awkward piece for cheap hosting — most PaaS platforms don't include it. A serverless option like Upstash Kafka or Confluent Cloud's free tier avoids self-hosting a broker.
* **Frontend:** static, zero build step — deploys as-is to GitHub Pages, Netlify, or Vercel.
* **Secrets:** `APP_JWT_SECRET` and DB/Redis/Kafka credentials go into the platform's secret manager, never the repo.
* **Required code change:** update `app.cors.allowed-origin` to the real frontend URL once it's known (defaults to `http://localhost:5500`).

This gets a clickable public demo, not a production-grade deployment — no HA, observability, or CI/CD yet (see Phase 4/5 in the phase table above).

## What's Next

Phase 4 (observability, resilience, Docker, CI/CD) and Phase 5 (Kubernetes, HA, disaster recovery, performance engineering).

## License

TBD (leaning MIT)
