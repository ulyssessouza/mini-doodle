# doodle-coding-challenge

A mini meeting-scheduling platform (Doodle-style) built with Spring Boot 4 and PostgreSQL.

Users register, manage **available time slots** in their personal calendar, book
slots as **meetings** with participants, and query an aggregated **free/busy
view** for any time frame. Booking a slot is rejected if it overlaps busy time
of the organizer or of any participant who is a registered user. *Calendar*
exists purely as a domain concept — there is no Calendar entity or endpoint.

## Tech stack

- Java 17, Spring Boot 4 (Web MVC, Data JPA, Validation, Actuator)
- PostgreSQL 17, Flyway migrations
- springdoc OpenAPI 3 (Swagger UI), Micrometer + Prometheus metrics
- JUnit 5, Mockito, Testcontainers

## Run with docker-compose

```bash
docker compose up --build
```

- API: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/actuator/health
- Metrics (Prometheus format): http://localhost:8080/actuator/prometheus

Stop with `docker compose down` (add `-v` to wipe the database).

### Configuration

Host ports are configurable via environment variables — useful when 8080 or
5432 are already taken on your machine:

| Variable   | Default | Purpose                                        |
|------------|---------|------------------------------------------------|
| `APP_PORT` | `8080`  | Host port exposing the API (container listens on 8080) |
| `DB_PORT`  | `5432`  | Host port exposing PostgreSQL                  |

```bash
APP_PORT=8081 DB_PORT=5433 docker compose up --build   # API reachable on :8081
```

For the development flow (database in compose, app via Gradle), an overridden
`DB_PORT` must be mirrored into the datasource URL:

```bash
DB_PORT=5433 docker compose up postgres -d
POSTGRES_URL=jdbc:postgresql://localhost:5433/doodle ./gradlew bootRun
```

`POSTGRES_USER` / `POSTGRES_PASSWORD` (defaults `doodle`) set the datasource
credentials.

## Run for development

```bash
docker compose up postgres -d   # database only
./gradlew bootRun               # app on :8080, expects localhost:5432
./gradlew test                  # unit + Testcontainers integration tests (Docker required)
```

## API walkthrough (curl)

All times are ISO-8601 with a UTC offset. Times in query strings use
`...Z` format directly. The examples build on each other in order.

### 1. Register users

```bash
curl -s -X POST localhost:8080/api/v1/users \
  -H 'Content-Type: application/json' \
  -d '{"name":"Alice","email":"alice@example.com"}'
# 201 {"id":"<aliceId>","name":"Alice","email":"alice@example.com","createdAt":"..."}

curl -s -X POST localhost:8080/api/v1/users \
  -H 'Content-Type: application/json' \
  -d '{"name":"Bob","email":"bob@example.com"}'
```

### 2. Create available slots (the organizer's calendar)

Create two slots for Alice: one to book later, one spare for the
modify/delete examples.

```bash
curl -s -X POST localhost:8080/api/v1/users/<aliceId>/slots \
  -H 'Content-Type: application/json' \
  -d '{"start":"2026-09-01T10:00:00Z","end":"2026-09-01T11:00:00Z"}'
# 201 {"id":"<slotId>","ownerId":"<aliceId>","start":"2026-09-01T10:00:00Z","end":"2026-09-01T11:00:00Z","status":"FREE","meetingId":null}

curl -s -X POST localhost:8080/api/v1/users/<aliceId>/slots \
  -H 'Content-Type: application/json' \
  -d '{"start":"2026-09-01T15:00:00Z","end":"2026-09-01T16:00:00Z"}'
# 201 {"id":"<spareSlotId>",...}
```

### 3. Query slots in a time frame

```bash
curl -s "localhost:8080/api/v1/users/<aliceId>/slots?from=2026-09-01T00:00:00Z&to=2026-09-02T00:00:00Z&status=FREE&page=0&size=50"
```

`from`/`to` are required ISO-8601 instants; `status` (`FREE`/`BUSY`) is
optional and matched case-insensitively (a blank value means no filter). The
result is a paginated `Page` of slots.

### 4. Modify a slot (reschedule / mark busy or free)

PATCH is partial: send only `start`, `end`, and/or `status`. Only free slots
can be rescheduled.

```bash
curl -s -X PATCH localhost:8080/api/v1/slots/<spareSlotId> \
  -H 'Content-Type: application/json' \
  -d '{"start":"2026-09-01T15:30:00Z","end":"2026-09-01T16:30:00Z"}'

curl -s -X PATCH localhost:8080/api/v1/slots/<spareSlotId> \
  -H 'Content-Type: application/json' -d '{"status":"BUSY"}'

# busy slots cannot be deleted:
curl -s -X DELETE localhost:8080/api/v1/slots/<spareSlotId>    # 409
curl -s -X PATCH localhost:8080/api/v1/slots/<spareSlotId> \
  -H 'Content-Type: application/json' -d '{"status":"FREE"}'
```

A slot that is booked as a meeting cannot be freed manually; cancel the
meeting instead (409).

### 5. Book a slot as a meeting

```bash
curl -s -X POST localhost:8080/api/v1/slots/<slotId>/book \
  -H 'Content-Type: application/json' \
  -d '{"title":"Design sync","description":"weekly","participants":[{"name":"Bob","email":"bob@example.com"}]}'
# 201 {"id":"<meetingId>",...,"participants":[{"name":"Bob","email":"bob@example.com","userId":"<bobId>"}]}
# Location: /api/v1/meetings/<meetingId>
```

The slot becomes `BUSY` and links to the meeting. If the range overlaps busy
time of the organizer or of any registered participant (guests who are not
registered users are not checked), the API returns `409` with a ProblemDetail
explaining whose calendar conflicts.

### 6. Free/busy calendar view

```bash
curl -s "localhost:8080/api/v1/users/<aliceId>/calendar?from=2026-09-01T00:00:00Z&to=2026-09-02T00:00:00Z&status=busy"
# [{"slotId":"...","start":"2026-09-01T10:00:00Z","end":"2026-09-01T11:00:00Z","status":"BUSY","meetingId":"...","title":"Design sync"}]
```

`from`/`to` are required ISO-8601 instants; `status` may be `free`, `busy`
(case-insensitive), or omitted for both. A registered user's busy view
includes meetings they organize, meetings they attend as a registered
participant, and manually busy slots. Slots overlapping the frame boundaries
are included.

### 7. Meetings

```bash
curl -s localhost:8080/api/v1/meetings/<meetingId>          # details + participants
curl -s "localhost:8080/api/v1/meetings?participant=bob@example.com"
curl -s -X DELETE localhost:8080/api/v1/meetings/<meetingId> # 204, frees the slot
```

### 8. Delete slots

```bash
curl -s -X DELETE localhost:8080/api/v1/slots/<slotId>        # 204 (free again after cancellation)
curl -s -X DELETE localhost:8080/api/v1/slots/<spareSlotId>   # 204
```

## Endpoints

| Method | Path                                        | Description                                        |
|--------|---------------------------------------------|----------------------------------------------------|
| POST   | `/api/v1/users`                             | Register a user (201)                              |
| GET    | `/api/v1/users/{id}`                        | Fetch a user                                       |
| POST   | `/api/v1/users/{userId}/slots`              | Create a slot (201)                                |
| GET    | `/api/v1/users/{userId}/slots`              | List slots in `[from, to)`, optional `status`, paginated |
| GET    | `/api/v1/slots/{slotId}`                    | Fetch a slot                                       |
| PATCH  | `/api/v1/slots/{slotId}`                    | Partial update: `start`, `end`, `status`           |
| DELETE | `/api/v1/slots/{slotId}`                    | Delete a free slot (204; 409 while busy)           |
| POST   | `/api/v1/slots/{slotId}/book`               | Book a meeting on the slot (201)                   |
| GET    | `/api/v1/users/{userId}/calendar`           | Aggregated free/busy view, optional `status`       |
| GET    | `/api/v1/meetings/{id}`                     | Meeting details incl. participants                 |
| GET    | `/api/v1/meetings?participant=<email>`      | Meetings for a participant email, paginated        |
| DELETE | `/api/v1/meetings/{id}`                     | Cancel a meeting, frees its slot (204)             |

## Error model

All errors use RFC 7807 `application/problem+json`:

| Status | Meaning |
|---|---|
| 400 | Validation failure (field errors) or invalid request (e.g. `end` before `start`) |
| 404 | Unknown user/slot/meeting |
| 409 | Booking overlap, duplicate email, invalid state transition |

## Design notes

- **Persistence:** the schema is managed by Flyway
  (`src/main/resources/db/migration/V1__init.sql`); slots carry an optimistic
  `@Version` for concurrent updates.
- **Concurrency:** booking pessimistically locks the involved user rows
  (`SELECT … FOR UPDATE` in a stable id order) inside the booking transaction,
  then re-checks an indexed overlap query with a locking read — two concurrent
  overlapping bookings cannot both succeed; the loser gets `409`. See
  *Why pessimistic locking for booking?* below for the rationale.
- **Performance:** range queries hit the index `(owner_id, start_at, end_at)`;
  the calendar view is one or two indexed queries per user (own slots plus
  attended meetings); participants are
  fetched with join-fetch (no N+1); list endpoints are paginated.
- **Emails:** uniqueness and lookups are case-insensitive (unique index on
  `lower(email)` in the database, case-insensitive repository queries).
- **Manual busy/free:** only *booking* is conflict-checked. Manually marking a
  slot busy/free is intentionally not checked against other busy time, so a
  user may hold overlapping busy slots; booking against that time is still
  rejected.
- Design decisions and rejected alternatives: see
  `docs/superpowers/specs/2026-08-29-doodle-scheduling-service-design.md`.

### Why pessimistic locking for booking?

Booking is a check-then-act sequence: read the target slot (must be `FREE`),
check overlapping busy time of everyone involved, then mark the slot `BUSY`
and insert the meeting. The conflict is **not a lost update on one row** — it
is a range-overlap conflict *across many rows*, so optimistic version checks
cannot enforce it:

- `@Version` on **Slot** only detects two writers of the *same* slot row. Two
  bookings of *different* slots that share a participant both pass version
  checks and would double-book that participant.
- An optimistic `@Version` on **User** would work only by force-writing every
  involved user's row on each booking: the loser discovers the conflict at
  flush time and needs a retry loop, and every booking pollutes user write
  sets.

`SELECT … FOR UPDATE` on the involved user rows (ordered by id — a stable
global order, so concurrent bookings cannot deadlock) makes any two bookings
sharing a user run one after the other. The overlap re-check runs *after* the
locks are held, so the loser sees the winner's committed `BUSY` slot and gets
a clean `409` ("overlaps existing busy time for: …") — deterministic
`201 + 409`, no client retries. Locks are held only for the short critical
section (a few indexed queries + one insert), so read traffic is unaffected.

Alternatives considered: **SERIALIZABLE isolation** (same guarantee, but
conflicts surface as SQLSTATE 40001 requiring whole-transaction retry
machinery) and a PostgreSQL **`tstzrange` + `EXCLUDE USING gist`** constraint
(bulletproof integrity, but fights JPA and cannot express "check the
organizer and all registered participants" cleanly).

Optimistic locking is still used where it fits: `@Version` on **Slot** guards
row-local races (concurrent PATCH/DELETE of the same slot → `409` "modified
concurrently; retry").
