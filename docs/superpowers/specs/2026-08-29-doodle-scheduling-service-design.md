# Mini Doodle — Meeting Scheduling Service: Design

Date: 2026-08-29
Status: Approved (brainstorming session with user)

## Overview

A high-performance REST service that simulates a Doodle-style meeting scheduling
platform: users manage available time slots in their personal calendars, book
slots as meetings with participants, and query aggregated free/busy views for a
time frame. Built with Spring Boot 4 (Web MVC, Data JPA, Actuator) and
PostgreSQL, runnable locally via docker-compose.

### Requirements summary (from ASSIGNMENT.md)

- **Time slot management**: create available slots with configurable duration,
  modify/delete slots, mark slots busy or free.
- **Meeting scheduling**: convert available slots into meetings with title,
  description, and participants.
- **Free/busy queries**: aggregated view for a selected time frame.
- **Persistence**: all data in PostgreSQL.
- **Scale assumption**: hundreds of users, thousands of slots.
- **Plus items**: tests, OpenAPI documentation, metrics.
- *Calendar* exists only as a domain concept (a user's collection of
  slots/meetings) — there is no Calendar entity, table, or API resource.

### Decisions made with the user

1. **User identity**: no auth; callers identify users explicitly by ID in the
   URL. (User choice)
2. **Conflicts**: booking a slot is rejected (HTTP 409) if it overlaps busy
   time of the organizer or of any registered participant. (User choice)
3. **Slot booking semantics**: the slot stays and is marked `BUSY`, keeping a
   link to the meeting; deleting/cancelling the meeting frees the slot back to
   `FREE`. (User choice)
4. **Participants (Doodle behavior)**: Doodle does not require registration to
   participate — participants join by name/email. Therefore participants are
   stored as `name` + `email`; if the email matches a registered user, that
   user's calendar is conflict-checked; otherwise the participant is a guest.
5. **Plus items**: tests, OpenAPI/Swagger, and Actuator metrics are all in
   scope. (User choice)
6. **Architecture**: layered JPA (web → service → repository) with
   transactional conflict checks. Rejected alternatives: PostgreSQL
   `tstzrange` exclusion constraints (fights JPA, too much cleverness for the
   scope) and hexagonal architecture (over-engineered here). (User choice)

## Architecture

Layered Spring Boot application:

```
web (REST controllers, DTOs, ProblemDetail errors)
  └── service (domain rules, transactions, conflict checks)
        └── repository (Spring Data JPA)
              └── PostgreSQL (Flyway-managed schema)
```

Package layout under `com.doodle.doodlecodingchallenge`:

- `user/` — User domain, repository, API
- `slot/` — Slot domain, repository, API
- `meeting/` — Meeting + participant domain, repository, API
- `calendar/` — free/busy aggregated view API (read-only service over slots/meetings)
- `common/` — error handling, validation helpers

No spring-session-jdbc (no auth) — dependency removed to keep the stack lean.

## Domain model

- **User** — `id` UUID, `name`, `email` (unique). Registered via API.
- **Slot** — `id` UUID, `owner` FK User, `start`/`end` `timestamptz`
  (`end > start`, validated), `status` `FREE` | `BUSY`, `@Version` for
  optimistic locking. Index `(owner_id, start, end)`. Duration is derived from
  start/end, not stored separately.
- **Meeting** — `id` UUID, `title`, `description`, `organizer` FK User
  (equals slot owner), `slot` FK unique (one meeting per slot), `createdAt`.
- **MeetingParticipant** — `id`, `meeting` FK, `displayName`, `email`,
  optional `user` FK when the email belongs to a registered user; unique
  `(meeting_id, email)`.

### Domain rules

- Creating a slot always yields a `FREE` slot.
- A slot can be rescheduled (start/end change) or deleted while `FREE`.
  Rescheduling/deleting a `BUSY` slot is rejected (busy time must be managed
  via its meeting or via mark-free).
- Mark busy/free manually: allowed; marking `BUSY` without a meeting blocks
  that time without creating a meeting.
- **Booking**: only the slot owner may book their own slot (they become the
  meeting organizer). Booking converts a `FREE` slot into a `Meeting`; slot
  becomes `BUSY` and links to the meeting. Rejected (409) if the slot's range
  overlaps any `BUSY` slot of the organizer or of any participant who is a
  registered user.
- **Calendar view**: `busy` returns the user's booked meetings plus manually
  busy slots in the frame; `free` returns their `FREE` slots; omitted `status`
  returns both.
- **Cancelling/deleting a meeting** sets its slot back to `FREE`.
- A user cannot book overlapping time against themselves either — the
  organizer (slot owner) is included in the conflict check.

## API design (v1, JSON)

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/v1/users` | Register user (`name`, `email`) |
| GET | `/api/v1/users/{userId}` | Get user |
| POST | `/api/v1/users/{userId}/slots` | Create available slot (`start`, `end` ISO-8601 UTC) |
| GET | `/api/v1/users/{userId}/slots?from=&to=&status=&page=&size=` | List own slots in a time frame, optional status filter |
| GET | `/api/v1/slots/{slotId}` | Slot details |
| PATCH | `/api/v1/slots/{slotId}` | Reschedule (new start/end) or set `status` |
| DELETE | `/api/v1/slots/{slotId}` | Delete slot (only when `FREE`) |
| POST | `/api/v1/slots/{slotId}/book` | Book as meeting: `title`, `description`, `participants: [{name, email}]` |
| GET | `/api/v1/meetings/{meetingId}` | Meeting with participants |
| DELETE | `/api/v1/meetings/{meetingId}` | Cancel meeting (frees its slot) |
| GET | `/api/v1/meetings?participant={email}&page=&size=` | Meetings a participant (email) attends |
| GET | `/api/v1/users/{userId}/calendar?from=&to=&status=free|busy` | Aggregated free/busy view for the time frame |

- Times are ISO-8601 with UTC offsets; stored as `timestamptz` (UTC).
- Responses include links to related resources where useful (e.g. slot →
  meeting).
- Errors: RFC 7807 `ProblemDetail` bodies — `400` validation, `404` unknown
  ids, `409` conflicts (booking overlap, invalid state transition), `415`/`405`
  defaults from Spring.
- OpenAPI 3 via springdoc; Swagger UI at `/swagger-ui.html`.

## Concurrency & performance

- Booking and slot mutations run in `@Transactional` services.
- Conflict detection inside the booking transaction: locking read
  (`SELECT … FOR UPDATE` via a JPA lock query) of the organizer's and
  registered participants' slots overlapping the candidate range, then
  re-validation. Two concurrent bookings of overlapping time cannot both
  succeed; the loser gets `409`.
- Optimistic `@Version` on Slot as a second line of defense for concurrent
  PATCH/DELETE.
- Aggregated calendar view: one indexed range query on
  `(owner_id, start, end)` per user; no N+1 (participants via `JOIN FETCH`);
  pagination on all list endpoints. This comfortably serves hundreds of users
  with thousands of slots.

## Testing

- **Unit tests** (Mockito/JUnit 5): slot state transitions, conflict-detection
  logic, participant matching.
- **@WebMvcTest slices**: validation, ProblemDetail error handling.
- **Integration tests** (Testcontainers PostgreSQL): end-to-end API flows —
  register user, create slot, book meeting, conflict rejection, cancel frees
  slot, calendar aggregation with mixed free/busy data.

## Operations

- `compose.yaml`: `app` service (multi-stage Dockerfile, Java 17) +
  `postgres:17` with healthchecks; app `depends_on` postgres with
  `condition: service_healthy`.
- Flyway manages schema (`V1__init.sql`).
- Actuator endpoints: `/actuator/health`, `/actuator/info`,
  `/actuator/prometheus` (micrometer-registry-prometheus).
- README documents how to run and consume the service with curl examples.

## Git & delivery

- Repository initialized with an initial README commit; regular, meaningful
  commits per feature slice.
- Deliverable: repository link; runnable with `docker compose up --build`.
