# event-service

Group 8 (Events, Communications & Feedback) microservice for the University Services Management Platform. It owns **events, registrations and capacity**: organizers create and publish events, users register and cancel, and organizers and administrators see participation summaries.

It has its own MySQL database that no other service reads. Other services use its REST API. Announcements, notifications and feedback live in the separate `communication-feedback-service`.

## Contents

- [Stack](#stack)
- [Run it](#run-it)
- [Configuration](#configuration)
- [Security and roles](#security-and-roles)
- [Integrations](#integrations)
- [API](#api)
- [Database](#database)
- [Testing](#testing)
- [Deployment](#deployment)
- [Troubleshooting](#troubleshooting)

## Stack

Java 17 · Spring Boot 4.0.8 · Maven · MySQL 8 · Spring Data JPA (Hibernate) · Flyway · Spring Security (Group 5 RS256 JWT) · Bean Validation · springdoc-openapi (Swagger UI) · JUnit 5 / Mockito · Docker · GitHub Actions

Layered as controller → service → repository, with request/response DTOs. Spring Boot 4.0.8 is used because 3.x is no longer offered by Spring Initializr.

## Run it

Prerequisites: JDK 17+, Docker Desktop. Maven is optional (`./mvnw` is included).

### Daily development (app in IntelliJ, MySQL in Docker)

```bash
docker compose up -d mysql
```

Run `EventServiceApplication` from IntelliJ with these environment variables (Run → Edit Configurations → Environment variables):

```
DB_URL=jdbc:mysql://localhost:3307/event_service_db;DB_USERNAME=group8;DB_PASSWORD=group8;SPRING_PROFILES_ACTIVE=dev,seed
```

`dev` enables the test-token endpoint and `seed` loads demo data. MySQL restarts with Docker Desktop and keeps its data.

- API: `http://localhost:8081`
- Swagger UI: `http://localhost:8081/swagger-ui/index.html`
- Health: `http://localhost:8081/actuator/health`

### Everything in Docker (service + its own MySQL)

```bash
docker compose --profile app up --build
```

The app container is opt-in (compose profile `app`) so it never takes port 8081 while you run the app from IntelliJ. MySQL is exposed on host port **3307** to avoid clashing with a local MySQL install.

### Without Docker

Point `DB_URL`, `DB_USERNAME` and `DB_PASSWORD` at any MySQL 8 database (created as below), then `./mvnw spring-boot:run`.

```sql
CREATE DATABASE event_service_db;
CREATE USER 'group8'@'%' IDENTIFIED BY 'group8';
GRANT ALL PRIVILEGES ON event_service_db.* TO 'group8'@'%';
```

Tables are created by Flyway on startup.

## Configuration

Everything is set through environment variables; defaults suit local development.

| Variable | Default | Purpose |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | none | `dev` = test-token endpoint, `seed` = demo data, `docker` = deployment (DB variables required). Never use `dev` or `seed` in a shared deployment |
| `PORT` / `SERVER_PORT` | `8081` | HTTP port (`PORT` is set by Render) |
| `DB_URL` | `jdbc:mysql://localhost:3306/event_service_db` | JDBC URL |
| `DB_USERNAME` / `DB_PASSWORD` | `group8` / `group8` | Database credentials |
| `GROUP5_BASE_URL` | `http://localhost:8001` (`http://identity-service:8001` in `docker`) | Group 5 Identity Service |
| `GROUP5_MOCK` | `true` | `true` = every user is eligible, Group 5 is not called |
| `GROUP5_CONNECT_TIMEOUT` / `GROUP5_READ_TIMEOUT` | `PT10S` / `PT60S` | Group 5 sleeps when idle on Render and its first answer can take about a minute |
| `JWT_JWKS_URI` | `{GROUP5_BASE_URL}/.well-known/jwks.json` | Group 5 public keys used to verify tokens |
| `JWT_ISSUER` / `JWT_AUDIENCE` | `university-identity-service` / `university-services-platform` | Required `iss` / `aud` of every token |
| `GROUP6_BASE_URL` | `http://localhost:9002` | Group 6 facility-resource-service |
| `GROUP6_MOCK` | `true` | `true` = every venue is valid, Group 6 is not called |
| `NOTIFICATIONS_BASE_URL` | `http://localhost:8082` | communication-feedback-service |
| `NOTIFICATIONS_MOCK` | `true` | `true` = notifications are only logged |
| `NOTIFICATIONS_SERVICE_KEY` | empty | Shared `X-Service-Key` for the notification API. Keep it out of Git |
| `NOTIFICATIONS_CONNECT_TIMEOUT` / `NOTIFICATIONS_READ_TIMEOUT` | `PT10S` / `PT90S` | The notification service sleeps when idle; sending is in the background, so waiting never slows users |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173,http://localhost:3000` | Frontend origins allowed to call the API from a browser. Empty if the API Gateway handles CORS |
| `EVENTS_AUTO_COMPLETE_ENABLED` / `EVENTS_AUTO_COMPLETE_INTERVAL` | `true` / `PT5M` | Job that marks published events COMPLETED after they end |

## Security and roles

Users log in on **Group 5's Identity Service** and call this API with `Authorization: Bearer <token>`. event-service verifies the RS256 signature with Group 5's public keys (JWKS), plus expiry, issuer and audience. It reads the user id from `sub` (e.g. `usr-student-001`) and the roles from `roles`. It never issues tokens itself, except the `dev`-profile test endpoint.

| Action | Roles |
|---|---|
| Create events | EVENT_ORGANIZER, ACADEMIC_STAFF, ADMIN |
| Edit, publish, complete, cancel an event | Its organizer (EVENT_ORGANIZER / ACADEMIC_STAFF) or ADMIN |
| See all events incl. drafts, overall summary | ADMIN, ADMINISTRATIVE_STAFF |
| Per-event summary | The event's organizer, ADMIN, ADMINISTRATIVE_STAFF |
| Register, cancel own registration, list own registrations | Any signed-in user (registration also checks the event's eligibility rule) |

Every rule is enforced in the service layer, whatever the frontend shows.

## Integrations

Each integration has a mock switch (default on), so the service runs on its own. Group 5 calls use a 10 s connect / 60 s read timeout and notifications 10 s / 90 s (both services sleep when idle on Render); Group 6 3 s / 5 s. An outage or timeout never counts as success.

| Service | When | Call | If it is down |
|---|---|---|---|
| **Group 5** Identity Service (`https://university-identity-service.onrender.com`) | Every request (token check, keys cached); registration (eligibility) | `GET /.well-known/jwks.json`; one call per registration with the user's token: `GET /api/v1/validation/users/{userId}` (roles-only rules) or `.../eligibility?relationship=AFFILIATION&department_id=CS` (department / faculty rules) | 401 if keys cannot be fetched; registration returns 503 `GROUP5_UNAVAILABLE`, nothing is saved |
| **Group 6** facility-resource-service | Publishing a physical event | `GET /api/resources/code/{code}/validate` | 503 `GROUP6_UNAVAILABLE`, the event stays DRAFT |
| **communication-feedback-service** (`https://notification-and-feedback-uni-service.onrender.com`) | After a registration or event change is saved | `POST /api/notifications/trigger` with `X-Service-Key` ([contract](docs/notification-api-contract.yaml)) | Logged only; the user's action is never undone or slowed down |

**Eligibility rules** are stored per event as JSON: `{"all": true}` (anyone; Group 5 is not asked) or any of `roles`, `departmentId`, `facultyId`, e.g. `{"roles": ["STUDENT"], "departmentId": "CS"}`, evaluated by Group 5. Department and faculty values are Group 5 codes (`CS`, `FSC`). Department checks need Group 5's Directory Service; until it is deployed they return 503, so use roles-only rules for demos. See [data dictionary](docs/data-dictionary.md).

**Notifications sent:** REGISTRATION_CONFIRMED and REGISTRATION_CANCELLED to the student; EVENT_CANCELLED to every confirmed registrant; EVENT_UPDATED when a published event's date, time or venue changes. Sent in the background after the database commit, each with an idempotency key.

**Tracing and proxies.** Every response carries `X-Request-ID` (the API Gateway's id is reused, otherwise one is created). It appears in every log line as `[event-service,<id>]` and is forwarded to Group 5, Group 6 and the notification service. `X-Forwarded-*` headers are honoured, so Swagger shows the public address behind Render or the gateway.

## API

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/events` | Create a draft event |
| GET | `/api/events` | List visible events. Optional filters: `status`, `from`, `to`, `upcoming`, `mine`, `page`, `size`; total in `X-Total-Count` |
| GET | `/api/events/{id}` | Event detail |
| PATCH | `/api/events/{id}` | Partial update |
| PATCH | `/api/events/{id}/publish` | Publish (venue checked with Group 6) |
| PATCH | `/api/events/{id}/complete` | Mark finished (also automatic after the end time) |
| PATCH | `/api/events/{id}/cancel` | Cancel |
| POST | `/api/events/{eventId}/registrations` | Register the caller |
| PATCH | `/api/registrations/{id}/cancel` | Cancel own registration |
| GET | `/api/registrations/mine` | Own registrations |
| GET | `/api/events/{eventId}/registrations` | Registration and capacity summary for one event |
| GET | `/api/events/summary` | Totals across all events |

Every endpoint is also served under `/api/v1/...` (e.g. `/api/v1/events`), the form the API Gateway uses; `/api/...` keeps working.

Every error has the shape `{ "success": false, "error": { "code", "message" } }`. The full list of error codes is in Swagger.

**Frozen contract.** [`docs/event-service-openapi.json`](docs/event-service-openapi.json) is the published contract for the frontend and other services. `ApiContractTest` fails the build if the running API differs from it, so breaking changes can't slip in after the API freeze. After an intentional, agreed change, regenerate and commit it:

```bash
./mvnw test -Dtest=ApiContractTest -Dcontract.update=true
```

## Database

MySQL database `event_service_db`, owned only by this service. Tables, columns, statuses, rules and the ERD are in [docs/data-dictionary.md](docs/data-dictionary.md).

Schema changes are made **only** through Flyway migrations in `src/main/resources/db/migration`. `ddl-auto=validate` makes Hibernate check the entities against the schema and never change it. Never edit a migration that has already run anywhere; add a new version instead.

| Migration | Change |
|---|---|
| V1 | `events` table |
| V2 | `registrations` table (unique event + user, FK to events) |
| V3 | User ids widened to Group 5 format (`VARCHAR(64)`) |
| V4 | Indexes for own registrations, organizer lookups and the auto-complete job |

**Demo data:** the `seed` profile loads `db/seed/R__seed_demo_data.sql`, which has 6 events in every status and 5 registrations, with fixed ids matching the Postman collection. It is safe to re-run. **Reset** local data with `docker compose down -v` (deletes the MySQL volume), then start again.

## Testing

```bash
docker compose up -d mysql
./mvnw clean verify
```

Runs unit tests (business rules, security, Group 5/6 and notification clients against fake HTTP servers), integration tests against the real MySQL schema, and the API contract check. CI (GitHub Actions) runs the same on every PR and push to `main`, with its own MySQL.

**Postman:** import [`docs/event-service.postman_collection.json`](docs/event-service.postman_collection.json). It has 61 requests covering every endpoint and error code, with assertions. Start the app with `SPRING_PROFILES_ACTIVE=dev,seed`, then run the folders in order (folder 0 mints test tokens). Headless:

```bash
npx newman run docs/event-service.postman_collection.json --env-var baseUrl=http://localhost:8081
```

Against a real deployment (no `dev` profile), skip folder 0, log in on Group 5 (`POST /api/v1/auth/login`) and paste the `access_token` values into the token variables.

## Deployment

The `Dockerfile` builds a small JRE image with a health check on `/actuator/health`. It is deployed on **Render** (Docker) with an **Aiven** MySQL database. Merges to `main` pass CI and then deploy.

Render environment for the final setup:

```
SPRING_PROFILES_ACTIVE=docker
DB_URL=jdbc:mysql://<aiven-host>:<port>/event_service_db?sslMode=REQUIRED
DB_USERNAME=... DB_PASSWORD=...
GROUP5_BASE_URL=https://university-identity-service.onrender.com   GROUP5_MOCK=false
GROUP6_BASE_URL=<Group 6 URL>             GROUP6_MOCK=false
NOTIFICATIONS_BASE_URL=https://notification-and-feedback-uni-service.onrender.com   NOTIFICATIONS_MOCK=false
NOTIFICATIONS_SERVICE_KEY=<shared key>
CORS_ALLOWED_ORIGINS=<frontend URL, or empty behind the API Gateway>
```

Until a dependency is deployed, leave its `*_MOCK` unset (true). While Group 5 is unreachable, every request is 401 because tokens cannot be verified.

## Troubleshooting

| Symptom | Cause and fix |
|---|---|
| `Communications link failure` / connection refused on startup | MySQL is not running: start Docker Desktop, then `docker compose up -d mysql`. Check `DB_URL` uses port 3307 for the Docker database |
| `Port 8081 was already in use` | Another copy is running (IntelliJ, or the Docker `app` container). Stop it, or `docker compose stop event-service`, or set `SERVER_PORT` |
| Every request returns 401 | No valid token: use the `dev` profile token endpoint locally, or check `GROUP5_BASE_URL` / JWKS reachability in a deployment |
| Registration returns 503 `GROUP5_UNAVAILABLE` | `GROUP5_MOCK=false` but Group 5 is unreachable at `GROUP5_BASE_URL` |
| Publishing returns 503 `GROUP6_UNAVAILABLE` | `GROUP6_MOCK=false` but Group 6 is unreachable at `GROUP6_BASE_URL` |
| Log shows `Notification ... was not delivered` | communication-feedback-service is down or `NOTIFICATIONS_SERVICE_KEY` is wrong (401). The user's action still succeeded |
| Build fails in `ApiContractTest` | The API changed. Revert, or if the change is agreed, regenerate the contract (see [API](#api)) |
| Browser shows a CORS error | Add the frontend origin to `CORS_ALLOWED_ORIGINS` |
