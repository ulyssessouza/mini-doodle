# doodle-coding-challenge

A mini meeting-scheduling platform (Doodle-style) built with Spring Boot.

## Status

Work in progress — see `ASSIGNMENT.md` for the task and `docs/superpowers/specs/` for the design.

## Tech stack

- Java 17, Spring Boot 4 (Web MVC, Data JPA, Actuator)
- PostgreSQL 17 (via docker-compose)
- Flyway migrations, springdoc OpenAPI, Micrometer/Prometheus metrics
- JUnit 5 + Testcontainers for testing

## Running locally

```bash
docker compose up --build
```

The API will be available at `http://localhost:8080` (documentation and usage examples will be added as the service is implemented).
