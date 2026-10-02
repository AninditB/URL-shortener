← [Index](00-index.md)

## Overview

ShortLink is a URL shortener: it turns a long URL into a short, shareable code, redirects visitors from that code back to the original destination, and tracks click analytics (volume over time, country, device type) without slowing the redirect down. Users register, log in with a JWT, and manage their own links — create, list, disable, delete — through either a REST API or a small browser-based demo page.

Both the backend API and the frontend demo are complete and working end-to-end today.

**Tech stack**

| Layer | Technology |
| --- | --- |
| Language / framework | Java 21, Spring Boot 3.3 |
| Web / data | Spring Web, Spring Data JPA, Spring Security |
| Cache / coordination | Redis 7 (Spring Data Redis) |
| Event streaming | Apache Kafka (KRaft mode) |
| Database | PostgreSQL 16, Flyway migrations |
| Auth | JWT (`jjwt`), BCrypt password hashing |
| Geo lookup | MaxMind GeoIP2 (`GeoCountryResolver`) |
| Testing | JUnit 5, Mockito, Testcontainers |
| Frontend | Plain HTML/CSS/vanilla JS — no build tooling, served independently, calls the API cross-origin (CORS) |
