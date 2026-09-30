# event-service demo guide

A step-by-step script for demonstrating event-service (Group 8: events and registrations) on the deployed platform, and what to do if something goes wrong.

## Services and links

| Service | URL | Health check |
|---|---|---|
| event-service (this service) | `https://eventmanagement-uni-service-management.onrender.com` | `/actuator/health` |
| Swagger UI | `https://eventmanagement-uni-service-management.onrender.com/swagger-ui/index.html` | |
| Group 5 Identity Service | `https://university-identity-service.onrender.com` | `/health` |
| API Gateway (Group 5) | `https://university-api-gateway.onrender.com` | `/health/services` |
| communication-feedback-service (Kasun) | `https://notification-and-feedback-uni-service.onrender.com` | `/actuator/health` |

## Demo logins (Group 5)

All share one password, which Group 5 sent privately. Never write it in slides, Git or Jira.

| Username | User id | Role | Use in the demo |
|---|---|---|---|
| `EVO001` | `usr-organizer-001` | EVENT_ORGANIZER | Creates, edits, publishes, completes and cancels events |
| `STU001` | `usr-student-001` | STUDENT | Registers successfully |
| `ACD001` | `usr-academic-001` | ACADEMIC_STAFF | Refused by the eligibility rule |
| `ADM001` | `usr-admin-001` | ADMIN | Sees and manages everything |
| `ADS001` | `usr-adminstaff-001` | ADMINISTRATIVE_STAFF | Sees all events and the overview |
| `STU002` | `usr-student-002` | inactive | Login is refused by Group 5 |

## 10 minutes before the demo

1. **Wake everything.** All services run on Render's free plan and sleep after 15 minutes idle; waking can take several minutes. Open each health link above until it answers. event-service is kept awake by the `Keep event-service awake` GitHub Actions workflow, but check it anyway.
2. **Check Render settings** for event-service: `SPRING_PROFILES_ACTIVE=docker`, `GROUP5_MOCK=false`, `NOTIFICATIONS_MOCK=false` with the service key set.
3. **Open Postman** with `docs/event-service-live-integration.postman_collection.json`, `demoPassword` set as the Current value, and run folder 0 once.
4. **Log in once** as each demo user you will use. Tokens last 60 minutes, so log in again if the demo runs longer.

## Demo script (about 10 minutes)

Use the live Postman collection, or Swagger UI with **Authorize** and a token from the login.

1. **Architecture (1 min).**
   - event-service owns events and registrations in its own MySQL database (Aiven).
   - Every request carries a Group 5 token, which event-service verifies itself.
   - Venues are checked with Group 6; notifications go to communication-feedback-service.
   - The frozen API contract is `docs/event-service-openapi.json`.
2. **Create a draft event (`EVO001`).**
   - `POST /api/v1/events` with `"eligibilityRule": "{\"roles\": [\"STUDENT\"]}"`.
   - Show it is `DRAFT` and that the organizer is `usr-organizer-001`.
3. **Drafts are private.** As `STU001`, `GET /api/v1/events` does not list it; `GET /api/v1/events/{id}` returns 403 `NOT_VISIBLE`.
4. **Publish (`EVO001`).** `PATCH /api/v1/events/{id}/publish` → `PUBLISHED`. For a physical venue such as `LAB-101`, event-service checks it with Group 6 first.
5. **Register (`STU001`).**
   - `POST /api/v1/events/{id}/registrations` → 201 `CONFIRMED`.
   - Group 5 confirmed the STUDENT role live.
   - A REGISTRATION_CONFIRMED notification goes to communication-feedback-service; Kasun can show it arriving.
6. **Eligibility refused (`ACD001`).** The same request → 403 `NOT_ELIGIBLE`, "You do not have a role that can register for this event."
7. **Business rules.**
   - Register `STU001` again → 409 `ALREADY_REGISTERED`.
   - Without a token → 401.
   - Optional: a capacity-1 event, where the second student → 409 `CAPACITY_REACHED`.
8. **Summaries.**
   - `EVO001`: `GET /api/v1/events/{id}/registrations` shows confirmed, cancelled and remaining seats.
   - `ADS001`: `GET /api/v1/events/summary` shows totals.
9. **Change and cancel.**
   - `EVO001` moves the event: `PATCH /api/v1/events/{id}` with a new `scheduleEnd` → EVENT_UPDATED notification to registrants.
   - `STU001` cancels: `PATCH /api/v1/registrations/{id}/cancel` → REGISTRATION_CANCELLED notification.
   - `EVO001` cancels the event: `PATCH /api/v1/events/{id}/cancel`.
10. **Completion.**
    - Published events become `COMPLETED` automatically after they end (checked every 5 minutes).
    - Organizers can complete a started event early with `PATCH /api/v1/events/{id}/complete`.
    - Feedback is only accepted for completed events.
11. **Quality (1 min).**
    - GitHub Actions runs 155 automated tests on every PR, including an API contract check.
    - The Postman collections have 61 and 15 checked requests.
    - Evidence reports are attached to Jira USMG8-225.

## If something goes wrong

| What you see | Why | What to do |
|---|---|---|
| A request hangs for a minute or more | A service was asleep | Wait; it answers once awake. Next time wake everything first |
| 401 on every request | Token expired (60 min) or login failed | Log in again. If it persists, open the Group 5 health link: event-service needs Group 5's keys to verify tokens |
| 503 `GROUP5_UNAVAILABLE` on registration | Group 5 is down or slow, or the rule uses a department (Group 5's Directory Service is not live yet) | Show it as the designed safe failure: nothing was saved. Use roles-only rules for the demo |
| 503 `GROUP6_UNAVAILABLE` on publish | Group 6 is down | Show the safe failure (event stays DRAFT), then publish an online event instead |
| Registration works but no notification arrives | Notification service asleep or key wrong | The registration still succeeded by design. Check the event-service Render log for `was not delivered` |
| event-service itself is down | Render problem | Run it locally: `docker compose up -d mysql`, start the app with `SPRING_PROFILES_ACTIVE=dev,seed`, and use `docs/event-service.postman_collection.json` (dev tokens, no Group 5 needed) |

## After the demo

Cancel any test events you created (`PATCH /api/v1/events/{id}/cancel`) so they don't clutter the shared data.
