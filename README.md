# ShortLink — Phase 1: Core URL Shortener

Phase 1 of 5 in the [ShortLink roadmap](https://claude.ai/chat/url-shortener-roadmap.md). This phase is a self-contained, locally-runnable Java + PostgreSQL CRUD application — no caching, auth, or async processing yet. Those arrive in later phases.

## What This Phase Is

A clean, working URL shortener with no distributed-systems concerns. The goal here is Java fundamentals, REST API design, database design, and clean code — the foundation everything else builds on.

## Features

* Create a short URL
* Redirect a short URL to its original destination
* Get URL details
* Delete a URL
* URL expiration
* Custom aliases
* Basic input validation
* URL safety validation — rejects malformed URLs and blocks internal/private IP ranges and localhost, to prevent SSRF and open-redirect abuse from the start

## Tech Stack

* Java 21
* Spring Boot
* Spring Web
* Spring Data JPA
* PostgreSQL
* Flyway (migrations)
* JUnit 5

## Architecture

```text
Client
  │
  ▼
Java Application
  │
  ▼
PostgreSQL
```

```text
Controller
   ↓
Service
   ↓
Repository
   ↓
PostgreSQL
```

## Database Schema

```text
short_urls
──────────────────
id
short_code
original_url
status
created_at
updated_at
expires_at
```

`UNIQUE(short_code)`

### Short-code generation

Base62-encoded auto-increment ID: a row is inserted to obtain its database-assigned `id`, which is then Base62-encoded (`[0-9A-Za-z]`) into `short_code`. Simple and collision-free by construction, but it couples code generation to a single writer — this gets revisited in Phase 5 once multiple stateless app instances are writing concurrently (e.g. Snowflake IDs or a Redis-backed atomic counter).

## API

### Create a short URL

```http
POST /api/v1/urls
```

```json
{
  "originalUrl": "https://example.com/products/java",
  "customAlias": "java"
}
```

Response

```json
{
  "shortCode": "java",
  "shortUrl": "http://localhost:8080/java"
}
```

### Redirect

```http
GET /{shortCode}
```

### Get URL details

```http
GET /api/v1/urls/{id}
```

### Delete a URL

```http
DELETE /api/v1/urls/{id}
```

(Full endpoint list and request/response schemas will be documented via OpenAPI/Swagger as endpoints are implemented.)

## Getting Started

### Prerequisites

* Java 21
* Maven
* Docker (for local PostgreSQL via Docker Compose), or a locally running PostgreSQL instance

### Start PostgreSQL

```bash
docker compose up -d
```

This starts a `postgres:16` container with a `shortlink` database, matching the app's default connection settings.

### Run locally

```bash
mvn spring-boot:run
```

Flyway applies the schema migrations automatically on startup. The app listens on `http://localhost:8080`.

### Run tests

```bash
mvn test
```

### Configuration

The app reads its datasource from environment variables, defaulting to the Docker Compose setup above:

| Variable | Default |
| --- | --- |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/shortlink` |
| `SPRING_DATASOURCE_USERNAME` | `shortlink` |
| `SPRING_DATASOURCE_PASSWORD` | `shortlink` |
| `APP_BASE_URL` | `http://localhost:8080` |

### API Docs

Once running, Swagger UI is available at `http://localhost:8080/swagger-ui.html`.

## Status

✅ Complete — all Phase 1 functional requirements implemented and verified against a real PostgreSQL instance.

## What's Next

Phase 2 introduces Redis caching, authentication/authorization, rate limiting, and idempotent request handling. See the [full roadmap](https://claude.ai/chat/url-shortener-roadmap.md) for all five phases.

## License

TBD (leaning MIT)
