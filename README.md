# event-service

Group 8 — Events, Communications & Feedback. Owns event creation, publishing, and registration. Part of a multi-repo backend (see `communication-feedback-service`, a sibling repo, for announcements/notifications/feedback — the two never share a database).

## Stack

Java 17 · Spring Boot 4.0.8 · Maven · MySQL 8 · Spring Data JPA (Hibernate) · Flyway · Spring Security (Group 5 RS256 JWT) · Bean Validation · springdoc-openapi (Swagger UI)

> Note: Spring Boot 3.x is EOL on Spring Initializr as of this build; 4.0.8 is the closest available match to the original stack recommendation. Layered architecture (controller/service/repository) and every other requirement below are unaffected.

## Prerequisites

- JDK 17+
- Maven 3.9+
- Docker & Docker Compose

## Daily development (app in IntelliJ, MySQL in Docker)

```bash
docker compose up -d mysql
```

Then run `EventServiceApplication` from the IDE with `DB_URL=jdbc:mysql://localhost:3307/event_service_db`, `DB_USERNAME=group8`, `DB_PASSWORD=group8`. MySQL restarts automatically with Docker Desktop and keeps its data. The app container is opt-in (compose profile `app`), so it never grabs port 8081 on its own.

## Run everything in Docker (service + its own MySQL)

```bash
docker compose --profile app up --build
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
| `GROUP5_BASE_URL` | `http://localhost:8001` (`http://identity-service:8001` in the `docker` profile) | Group 5 Identity Service |
| `JWT_JWKS_URI` | `{GROUP5_BASE_URL}/.well-known/jwks.json` | Group 5 public keys used to verify tokens |
| `JWT_ISSUER` / `JWT_AUDIENCE` | `university-identity-service` / `university-services-platform` | Required `iss` / `aud` of every token |
| `GROUP5_MOCK` / `GROUP6_MOCK` | `true` | Toggle mock mode for the Group 5 (eligibility) / Group 6 (venue) HTTP clients |
| `GROUP6_BASE_URL` | local placeholder | Real base URL of Group 6 |

**Authentication (Group 5).** Users log in on Group 5's Identity Service and send its token as `Authorization: Bearer <token>`. event-service checks the RS256 signature against Group 5's public keys, plus expiry, issuer and audience, and reads the user id from `sub` and the roles from `roles`. It never issues tokens itself, except the dev-only endpoint below.

**Group 5 eligibility check.** On registration, unless the event is `{"all": true}`, event-service calls `GET {GROUP5_BASE_URL}/api/v1/validation/users/{userId}/eligibility` with the user's own token. Set `GROUP5_MOCK=false` to use the real service. If Group 5 is down the registration fails with 503 and is never allowed. Rule format and roles: see `docs/data-dictionary.md`.

**Group 6 venue check.** When a physical event is published, its `venue` (a Group 6 resource code such as `LAB-101`) is checked with `GET {GROUP6_BASE_URL}/api/resources/code/{code}/validate`. Set `GROUP6_MOCK=false` and `GROUP6_BASE_URL` to use the real service; Group 6 needs no token. Their default port is 8081, the same as this service, so run one of them on another port locally.

## Database

Schema is managed exclusively via Flyway migrations in `src/main/resources/db/migration` — `spring.jpa.hibernate.ddl-auto=validate`, so Hibernate never auto-generates schema; it only checks entities match what Flyway already created.

## Status

- [x] Scaffold + health check
- [x] Environment config
- [x] Database schema + Flyway migrations
- [x] JWT authentication + role-based authorization
- [x] Group 5 tokens (JWKS) and eligibility API, Group 6 venue API (both with mock switches)
- [x] Event CRUD
- [x] Registration flow

## Demo data

Start the app with `SPRING_PROFILES_ACTIVE=dev,seed` to load sample events and registrations (see `docs/data-dictionary.md`). The seed is safe to re-run and is never loaded without the `seed` profile.

## API testing (Postman)

Import `docs/event-service.postman_collection.json`. It has 47 requests covering every endpoint and every error code, with assertions on each.

1. Start the app with `SPRING_PROFILES_ACTIVE=dev` (this enables `POST /api/dev/token?userId=usr-organizer-001&roles=EVENT_ORGANIZER`, which mints Group 5-shaped test tokens with a key generated at startup). Never enable `dev` on a shared deployment: anyone could mint an ADMIN token.
2. Run the collection in order — folder 0 mints tokens, the rest use them. `baseUrl` defaults to `http://localhost:8081`.

Headless run: `npx newman run docs/event-service.postman_collection.json`

Against a real deployment (no dev endpoint), skip folder 0, log in on Group 5 (`POST /api/v1/auth/login`) and paste each `access_token` into the token variables.
