# ShortLink

A URL shortener built as a progressive, phase-by-phase engineering project: each phase is a complete, working system that introduces one major class of engineering problem — starting from a clean Java CRUD app and building up through caching/auth, event-driven analytics, production observability, and eventually distributed scale.

| Phase | Focus | Status |
| --- | --- | --- |
| 1 — Core URL Shortener | Java, REST API design, PostgreSQL | ✅ Complete |
| 2 — Production Backend | Redis caching, auth/authorization, rate limiting, idempotency | ✅ Complete |
| 3 — Scalable Platform | Kafka, event-driven click analytics | 🚧 In progress |
| 4 — Production Engineering | Observability, resilience, Docker, CI/CD | ⏳ Not started |
| 5 — Enterprise Platform | Kubernetes, HA, disaster recovery, performance engineering | ⏳ Not started |

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

**Scalable platform (Phase 3, in progress)**
* Kafka infrastructure for an event-driven click pipeline (topic + dead-letter topic, typed producer/consumer wiring) — landed
* Publishing click events, an analytics consumer, and a per-URL analytics endpoint are in progress; see `.claude/tickets/phase3/` for the full backlog

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
                                                     └──► Kafka ──► Analytics Consumer
                                                                       (in progress)
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

Caching, rate limiting, idempotency, and (in progress) click-event publishing are all implemented as cross-cutting concerns behind the existing service-layer methods — controllers stay unaware of Redis/Kafka's existence.

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

Protected/ownership-gated actions use a standard `Authorization: Bearer <token>` header. Full schemas are documented via OpenAPI/Swagger (see below).

## Database Schema

```text
short_urls                    users
──────────────────            ──────────────────
id                             id
short_code                     email
original_url                   password_hash
status                         role
created_at                     created_at
updated_at
expires_at
owner_id  (nullable, → users.id)
```

Schema evolves via versioned Flyway migrations (`src/main/resources/db/migration/`), one file per schema change, tracked from Phase 1 onward.

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

* **Phase 1 — Complete.** All functional requirements implemented and verified against a real PostgreSQL instance.
* **Phase 2 — Complete.** All functional requirements implemented and verified against real PostgreSQL + Redis, including a Testcontainers-backed full-stack integration test.
* **Phase 3 — In progress.** Kafka infrastructure is in place; click-event publishing, the analytics consumer, and the analytics query endpoint are not yet implemented.

## What's Next

Phase 3 continues with publishing click events on redirect, an idempotent analytics consumer with retry/dead-letter handling, and a per-URL analytics endpoint. Phase 4 and 5 (observability/resilience, then distributed scale) follow after that.

## License

TBD (leaning MIT)
