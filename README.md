# event-service

Group 8 — Events, Communications & Feedback. Owns event creation, publishing, and registration. Part of a multi-repo backend (see `communication-feedback-service`, a sibling repo, for announcements/notifications/feedback — the two never share a database).

## Stack

Java 17 · Spring Boot 4.0.8 · Maven · MySQL 8 · Spring Data JPA (Hibernate) · Flyway · Spring Security (JWT) · Bean Validation · springdoc-openapi (Swagger UI)

> Note: Spring Boot 3.x is EOL on Spring Initializr as of this build; 4.0.8 is the closest available match to the original stack recommendation. Layered architecture (controller/service/repository) and every other requirement below are unaffected.

## Prerequisites

- JDK 17+
- Maven 3.9+
- Docker & Docker Compose

## Run with Docker Compose (service + its own MySQL)

```bash
docker compose up --build
```

- App: `http://localhost:8081`
- Health: `http://localhost:8081/actuator/health`
- Swagger UI: `http://localhost:8081/swagger-ui/index.html`
- OpenAPI JSON: `http://localhost:8081/v3/api-docs`
- MySQL exposed on host port `3307` (mapped off default 3306 to avoid clashing with a local MySQL install)

## Run locally without Docker

Point at any MySQL 8 instance via env vars, then:

```bash
mvn spring-boot:run
```

Defaults (see `application.yml`) connect to `jdbc:mysql://localhost:3306/event_service_db` with user `group8` / password `group8` if no env vars are set — override `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` as needed.

## Configuration

| Env var | Default | Purpose |
|---|---|---|
| `SERVER_PORT` | `8081` | HTTP port |
| `DB_URL` | `jdbc:mysql://localhost:3306/event_service_db` | JDBC URL |
| `DB_USERNAME` / `DB_PASSWORD` | `group8` / `group8` | DB credentials |
| `JWT_SECRET` | dev default (insecure) | JWT signing key — override in any real deployment |
| `JWT_EXPIRATION_MS` | `3600000` | Token TTL |
| `GROUP5_MOCK` / `GROUP6_MOCK` | `true` | Toggle mock mode for the Group 5 (eligibility) / Group 6 (venue) HTTP clients |
| `GROUP5_BASE_URL` / `GROUP6_BASE_URL` | local placeholders | Real base URLs once those services exist |

## Database

Schema is managed exclusively via Flyway migrations in `src/main/resources/db/migration` — `spring.jpa.hibernate.ddl-auto=validate`, so Hibernate never auto-generates schema; it only checks entities match what Flyway already created.

## Status

- [x] Scaffold + health check
- [x] Environment config
- [x] Database schema + Flyway migrations
- [x] JWT authentication + role-based authorization
- [x] Group 5 / Group 6 mock clients
- [x] Event CRUD
- [ ] Registration flow
