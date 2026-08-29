# Mini Doodle Scheduling Service — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the approved spec (`docs/superpowers/specs/2026-08-29-doodle-scheduling-service-design.md`): a Spring Boot 4 + PostgreSQL REST service for time-slot management, meeting booking with conflict rejection, and aggregated free/busy calendar views, runnable via docker-compose, with OpenAPI docs, metrics, and tests.

**Architecture:** Layered (REST controllers → `@Transactional` services → Spring Data JPA repositories → PostgreSQL 17, schema via Flyway). Booking conflict detection locks the involved user rows (pessimistic, `TreeSet` for consistent lock order) then re-checks an indexed overlap query inside the transaction, so concurrent overlapping bookings cannot both succeed — the loser gets 409.

**Tech Stack:** Java 17 toolchain, Gradle 9.7.1 wrapper, Spring Boot 4.1.1 (webmvc, data-jpa, validation, actuator), PostgreSQL 17, Flyway, springdoc-openapi 3.1.0 (Boot 4 compatible, verified on Maven Central), micrometer-registry-prometheus, JUnit 5, Mockito, Testcontainers.

**Conventions for all tasks:** work in repo root `/home/ulysses/workspace/doodle-coding-challenge`; base package `com.doodle.doodlecodingchallenge`; run one test class with `./gradlew test --tests '<FQCN>'`; run everything with `./gradlew test`. Every task ends with a commit. DTO mapping happens inside services (within the transaction) to avoid lazy-loading leaks; controllers stay thin.

**API surface implemented by this plan (per spec):**

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/v1/users` | Register user |
| GET | `/api/v1/users/{userId}` | Get user |
| POST | `/api/v1/users/{userId}/slots` | Create slot |
| GET | `/api/v1/users/{userId}/slots?from=&to=&status=&page=&size=` | List own slots (from/to required) |
| GET / PATCH / DELETE | `/api/v1/slots/{slotId}` | Get / reschedule+mark / delete slot |
| POST | `/api/v1/slots/{slotId}/book` | Book slot as meeting |
| GET | `/api/v1/meetings/{meetingId}` | Meeting with participants |
| DELETE | `/api/v1/meetings/{meetingId}` | Cancel meeting (frees slot) |
| GET | `/api/v1/meetings?participant=&page=&size=` | Meetings by participant email |
| GET | `/api/v1/users/{userId}/calendar?from=&to=&status=` | Aggregated free/busy view |

Errors: RFC 7807 ProblemDetail — 400 validation, 404 unknown id, 409 conflict/invalid transition.

---

### Task 1: Commit scaffold, wire dependencies, application.yml

**Files:**
- Modify: `build.gradle`
- Create: `src/main/resources/application.yml`
- Delete: `src/main/resources/application.properties`
- Delete: `src/test/java/com/doodle/doodlecodingchallenge/DoodleCodingChallengeApplicationTests.java` (needs a DB; replaced by the Testcontainers suite in Task 8)

- [ ] **Step 1: Commit the Spring Boot scaffold untouched**

```bash
cat .gitignore   # verify build/, .gradle/, .idea/ ignored; if .idea is missing, append ".idea/" to .gitignore
git add .
git status --short   # must show only source/config files — no build/, .gradle/ or .idea/
git commit -m "chore: add Spring Boot project scaffold"
```

- [ ] **Step 2: Verify toolchain**

Run: `./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`. Local JDKs include temurin-17 (`~/.jdks/temurin-17.0.19`) and openjdk-21/25 in `/usr/lib/jvm`; toolchain 17 resolves.
If toolchain 17 cannot be resolved, switch `JavaLanguageVersion.of(17)` to `21` and use `eclipse-temurin:21-jdk`/`21-jre` in Task 10's Dockerfile.

- [ ] **Step 3: Update build.gradle — replace the whole file with**

```gradle
plugins {
    id 'java'
    id 'org.springframework.boot' version '4.1.1'
    id 'io.spring.dependency-management' version '1.1.7'
}

group = 'com.doodle'
version = '0.0.1-SNAPSHOT'
description = 'doodle-coding-challenge'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-jdbc'
    implementation 'org.springframework.boot:spring-boot-starter-webmvc'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    implementation 'org.flywaydb:flyway-core'
    implementation 'org.flywaydb:flyway-database-postgresql'
    implementation 'io.micrometer:micrometer-registry-prometheus'
    implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.0'
    runtimeOnly 'org.postgresql:postgresql'
    testImplementation 'org.springframework.boot:spring-boot-starter-data-jpa-test'
    testImplementation 'org.springframework.boot:spring-boot-starter-webmvc-test'
    testImplementation 'org.testcontainers:junit-jupiter'
    testImplementation 'org.testcontainers:postgresql'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

tasks.named('test') {
    useJUnitPlatform()
}

tasks.named('bootJar') {
    archiveFileName = 'app.jar'
}
```

Removed vs scaffold: `spring-boot-starter-session-jdbc` (+ its test starter) — no auth in scope; `spring-boot-docker-compose` — dev auto-start replaced by explicit compose usage in Task 10. Added: validation, actuator, flyway (+postgres module), prometheus registry, springdoc, Testcontainers (versions managed by the Boot BOM).

- [ ] **Step 4: Replace config with application.yml**

```bash
rm src/main/resources/application.properties
```

Create `src/main/resources/application.yml`:

```yaml
spring:
  application:
    name: doodle-coding-challenge
  datasource:
    url: ${POSTGRES_URL:jdbc:postgresql://localhost:5432/doodle}
    username: ${POSTGRES_USER:doodle}
    password: ${POSTGRES_PASSWORD:doodle}
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
  flyway:
    enabled: true

management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus

springdoc:
  swagger-ui:
    path: /swagger-ui.html
```

- [ ] **Step 5: Delete the placeholder context test**

```bash
rm src/test/java/com/doodle/doodlecodingchallenge/DoodleCodingChallengeApplicationTests.java
```

(Context startup is covered by Testcontainers tests in Task 8; the placeholder would fail without a DB.)

- [ ] **Step 6: Verify build**

Run: `./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "build: persistence, validation, docs and metrics stack"
```

---

### Task 2: Exceptions and ProblemDetail error handling

**Files:**
- Create: `src/main/java/com/doodle/doodlecodingchallenge/common/NotFoundException.java`
- Create: `src/main/java/com/doodle/doodlecodingchallenge/common/InvalidRequestException.java`
- Create: `src/main/java/com/doodle/doodlecodingchallenge/common/ConflictException.java`
- Create: `src/main/java/com/doodle/doodlecodingchallenge/common/ApiExceptionHandler.java`
- Test: `src/test/java/com/doodle/doodlecodingchallenge/common/ApiExceptionHandlerTest.java`

- [ ] **Step 1: Write the failing test**

`src/test/java/com/doodle/doodlecodingchallenge/common/ApiExceptionHandlerTest.java`:

```java
package com.doodle.doodlecodingchallenge.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(ApiExceptionHandlerTest.ThrowingController.class)
@Import(ApiExceptionHandler.class)
class ApiExceptionHandlerTest {

    @Autowired
    MockMvc mockMvc;

    record Req(@jakarta.validation.constraints.NotBlank String name) {}

    @RestController
    static class ThrowingController {

        @GetMapping("/boom/not-found")
        void notFound() {
            throw NotFoundException.of("Thing", 42);
        }

        @GetMapping("/boom/conflict")
        void conflict() {
            throw new ConflictException("time range overlaps existing busy time for: bob@example.com");
        }

        @GetMapping("/boom/bad-request")
        void badRequest() {
            throw new InvalidRequestException("end must be after start");
        }

        @GetMapping("/boom/param")
        void missingParam(@RequestParam String param) {
        }

        @PostMapping(path = "/boom/validation", consumes = "application/json")
        void validation(@jakarta.validation.Valid @org.springframework.web.bind.annotation.RequestBody Req request) {
        }
    }

    @Test
    void mapsNotFoundToProblemDetail() throws Exception {
        mockMvc.perform(get("/boom/not-found"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.title").value("Resource not found"))
            .andExpect(jsonPath("$.detail").value("Thing not found: 42"));
    }

    @Test
    void mapsConflictToProblemDetail() throws Exception {
        mockMvc.perform(get("/boom/conflict"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.title").value("Conflict"))
            .andExpect(jsonPath("$.detail").value("time range overlaps existing busy time for: bob@example.com"));
    }

    @Test
    void mapsInvalidRequestToProblemDetail() throws Exception {
        mockMvc.perform(get("/boom/bad-request"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("Invalid request"))
            .andExpect(jsonPath("$.detail").value("end must be after start"));
    }

    @Test
    void mapsMissingParameterToProblemDetail() throws Exception {
        mockMvc.perform(get("/boom/param"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("Invalid request"))
            .andExpect(jsonPath("$.detail").value("Missing required parameter: param"));
    }

    @Test
    void mapsBeanValidationToProblemDetailWithErrors() throws Exception {
        mockMvc.perform(post("/boom/validation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("Invalid request"))
            .andExpect(jsonPath("$.errors[0].field").value("name"));
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests 'com.doodle.doodlecodingchallenge.common.ApiExceptionHandlerTest'`
Expected: FAIL — compile error, the exception classes and handler do not exist.

- [ ] **Step 3: Implement**

`src/main/java/com/doodle/doodlecodingchallenge/common/NotFoundException.java`:

```java
package com.doodle.doodlecodingchallenge.common;

public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }

    public static NotFoundException of(String resource, Object id) {
        return new NotFoundException("%s not found: %s".formatted(resource, id));
    }
}
```

`src/main/java/com/doodle/doodlecodingchallenge/common/InvalidRequestException.java`:

```java
package com.doodle.doodlecodingchallenge.common;

public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
```

`src/main/java/com/doodle/doodlecodingchallenge/common/ConflictException.java`:

```java
package com.doodle.doodlecodingchallenge.common;

public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
```

`src/main/java/com/doodle/doodlecodingchallenge/common/ApiExceptionHandler.java`:

```java
package com.doodle.doodlecodingchallenge.common;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Resource not found", ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    ProblemDetail conflict(ConflictException ex) {
        return problem(HttpStatus.CONFLICT, "Conflict", ex.getMessage());
    }

    @ExceptionHandler(InvalidRequestException.class)
    ProblemDetail invalidRequest(InvalidRequestException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", ex.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail integrity(DataIntegrityViolationException ex) {
        return problem(HttpStatus.CONFLICT, "Conflict", "Operation violates a data constraint");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ProblemDetail noResource(NoResourceFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Resource not found", "No such path: " + ex.getResourcePath());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validation(MethodArgumentNotValidException ex) {
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "Invalid request", "Validation failed");
        pd.setProperty("errors", ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> java.util.Map.of("field", fe.getField(),
                "message", fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage()))
            .toList());
        return pd;
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ProblemDetail missingParam(MissingServletRequestParameterException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request",
            "Missing required parameter: " + ex.getParameterName());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail typeMismatch(MethodArgumentTypeMismatchException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", "Invalid value for: " + ex.getName());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail unreadable(HttpMessageNotReadableException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", "Malformed request body");
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        return pd;
    }
}
```

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew test --tests 'com.doodle.doodlecodingchallenge.common.ApiExceptionHandlerTest'`
Expected: PASS (5 tests). If `MethodArgumentTypeMismatchException`/`NoResourceFoundException` moved packages in Framework 7, use the package the compiler suggests.

- [ ] **Step 5: Commit**

```bash
git add src
git commit -m "feat: problem-detail exception handling"
```

---

### Task 3: User registration and lookup

**Files:**
- Create: `src/main/java/com/doodle/doodlecodingchallenge/user/User.java`
- Create: `src/main/java/com/doodle/doodlecodingchallenge/user/dto/CreateUserRequest.java`
- Create: `src/main/java/com/doodle/doodlecodingchallenge/user/dto/UserDto.java`
- Create: `src/main/java/com/doodle/doodlecodingchallenge/user/UserRepository.java`
- Create: `src/main/java/com/doodle/doodlecodingchallenge/user/UserService.java`
- Create: `src/main/java/com/doodle/doodlecodingchallenge/user/UserController.java`
- Test: `src/test/java/com/doodle/doodlecodingchallenge/user/UserServiceTest.java`
- Test: `src/test/java/com/doodle/doodlecodingchallenge/user/UserControllerTest.java`

- [ ] **Step 1: Write the failing service test**

`src/test/java/com/doodle/doodlecodingchallenge/user/UserServiceTest.java`:

```java
package com.doodle.doodlecodingchallenge.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.doodle.doodlecodingchallenge.common.ConflictException;
import com.doodle.doodlecodingchallenge.common.NotFoundException;
import com.doodle.doodlecodingchallenge.user.dto.CreateUserRequest;
import com.doodle.doodlecodingchallenge.user.dto.UserDto;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    UserRepository users;

    @Test
    void registerCreatesUser() {
        UserService service = new UserService(users);
        when(users.existsByEmailIgnoreCase("alice@example.com")).thenReturn(false);
        when(users.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserDto dto = service.register(new CreateUserRequest("Alice", "alice@example.com"));

        assertThat(dto.name()).isEqualTo("Alice");
        assertThat(dto.email()).isEqualTo("alice@example.com");
        assertThat(dto.id()).isNotNull();
        assertThat(dto.createdAt()).isNotNull();
    }

    @Test
    void registerRejectsDuplicateEmail() {
        UserService service = new UserService(users);
        when(users.existsByEmailIgnoreCase("alice@example.com")).thenReturn(true);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> service.register(new CreateUserRequest("Alice", "alice@example.com")))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("alice@example.com");
    }

    @Test
    void getReturnsMappedUser() {
        UserService service = new UserService(users);
        User user = new User(UUID.randomUUID(), "Alice", "alice@example.com", Instant.now());
        when(users.findById(user.getId())).thenReturn(java.util.Optional.of(user));

        UserDto dto = service.get(user.getId());

        assertThat(dto.id()).isEqualTo(user.getId());
        assertThat(dto.email()).isEqualTo("alice@example.com");
    }

    @Test
    void getUnknownUserThrowsNotFound() {
        UserService service = new UserService(users);
        when(users.findById(UUID.randomUUID())).thenReturn(java.util.Optional.empty());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.get(UUID.randomUUID()))
            .isInstanceOf(NotFoundException.class);
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew test --tests 'com.doodle.doodlecodingchallenge.user.UserServiceTest'`
Expected: FAIL — compile error (User/UserRepository/UserService/DTOs missing).

- [ ] **Step 3: Implement the user feature**

`src/main/java/com/doodle/doodlecodingchallenge/user/User.java`:

```java
package com.doodle.doodlecodingchallenge.user;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected User() {
    }

    public User(UUID id, String name, String email, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
```

`src/main/java/com/doodle/doodlecodingchallenge/user/dto/CreateUserRequest.java`:

```java
package com.doodle.doodlecodingchallenge.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CreateUserRequest(
        @NotBlank String name,
        @NotBlank @Email String email) {
}
```

`src/main/java/com/doodle/doodlecodingchallenge/user/dto/UserDto.java`:

```java
package com.doodle.doodlecodingchallenge.user.dto;

import java.time.Instant;
import java.util.UUID;

import com.doodle.doodlecodingchallenge.user.User;

public record UserDto(UUID id, String name, String email, Instant createdAt) {

    public static UserDto from(User user) {
        return new UserDto(user.getId(), user.getName(), user.getEmail(), user.getCreatedAt());
    }
}
```

`src/main/java/com/doodle/doodlecodingchallenge/user/UserRepository.java`:

```java
package com.doodle.doodlecodingchallenge.user;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {

    boolean existsByEmailIgnoreCase(String email);

    Optional<User> findByEmailIgnoreCase(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id in :ids")
    List<User> findAllByIdForUpdate(@Param("ids") Collection<UUID> ids);
}
```

`src/main/java/com/doodle/doodlecodingchallenge/user/UserService.java`:

```java
package com.doodle.doodlecodingchallenge.user;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.doodle.doodlecodingchallenge.common.ConflictException;
import com.doodle.doodlecodingchallenge.common.NotFoundException;
import com.doodle.doodlecodingchallenge.user.dto.CreateUserRequest;
import com.doodle.doodlecodingchallenge.user.dto.UserDto;

@Service
public class UserService {

    private final UserRepository users;

    public UserService(UserRepository users) {
        this.users = users;
    }

    @Transactional
    public UserDto register(CreateUserRequest request) {
        if (users.existsByEmailIgnoreCase(request.email())) {
            throw new ConflictException("A user with email %s already exists".formatted(request.email()));
        }
        User user = new User(UUID.randomUUID(), request.name(), request.email(), Instant.now());
        return UserDto.from(users.save(user));
    }

    @Transactional(readOnly = true)
    public UserDto get(UUID id) {
        return users.findById(id)
            .map(UserDto::from)
            .orElseThrow(() -> NotFoundException.of("User", id));
    }
}
```

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew test --tests 'com.doodle.doodlecodingchallenge.user.UserServiceTest'`
Expected: PASS (4 tests).

- [ ] **Step 5: Write the failing controller test**

`src/test/java/com/doodle/doodlecodingchallenge/user/UserControllerTest.java`:

```java
package com.doodle.doodlecodingchallenge.user;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.doodle.doodlecodingchallenge.common.NotFoundException;
import com.doodle.doodlecodingchallenge.user.dto.UserDto;

@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    UserService userService;

    @Test
    void registerReturns201WithLocation() throws Exception {
        UUID id = UUID.randomUUID();
        when(userService.register(org.mockito.ArgumentMatchers.any(com.doodle.doodlecodingchallenge.user.dto.CreateUserRequest.class)))
            .thenReturn(new UserDto(id, "Alice", "alice@example.com", Instant.now()));

        mockMvc.perform(post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Alice\",\"email\":\"alice@example.com\"}"))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", "/api/v1/users/" + id))
            .andExpect(jsonPath("$.name").value("Alice"));
    }

    @Test
    void registerWithInvalidEmailReturns400WithFieldError() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Alice\",\"email\":\"not-an-email\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("Invalid request"))
            .andExpect(jsonPath("$.errors[0].field").value("email"));
    }

    @Test
    void getUnknownUserReturns404ProblemDetail() throws Exception {
        when(userService.get(org.mockito.ArgumentMatchers.any(UUID.class)))
            .thenThrow(NotFoundException.of("User", 42));

        mockMvc.perform(get("/api/v1/users/" + UUID.randomUUID()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.title").value("Resource not found"));
    }
}
```

- [ ] **Step 6: Run to verify failure, then implement the controller**

Run: `./gradlew test --tests 'com.doodle.doodlecodingchallenge.user.UserControllerTest'`
Expected: FAIL — `UserController` missing.

`src/main/java/com/doodle/doodlecodingchallenge/user/UserController.java`:

```java
package com.doodle.doodlecodingchallenge.user;

import java.net.URI;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.doodle.doodlecodingchallenge.user.dto.CreateUserRequest;
import com.doodle.doodlecodingchallenge.user.dto.UserDto;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    ResponseEntity<UserDto> register(@Valid @RequestBody CreateUserRequest request) {
        UserDto created = userService.register(request);
        return ResponseEntity.created(URI.create("/api/v1/users/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    UserDto get(@PathVariable UUID id) {
        return userService.get(id);
    }
}
```

- [ ] **Step 7: Run to verify pass**

Run: `./gradlew test --tests 'com.doodle.doodlecodingchallenge.user.*'`
Expected: PASS (7 tests).

- [ ] **Step 8: Commit**

```bash
git add src
git commit -m "feat: user registration and lookup"
```

---

### Task 4: Slot schema, entity, repository and service

**Files:**
- Create: `src/main/resources/db/migration/V1__init.sql`
- Create: `src/main/java/com/doodle/doodlecodingchallenge/slot/SlotStatus.java`
- Create: `src/main/java/com/doodle/doodlecodingchallenge/slot/Slot.java`
- Create: `src/main/java/com/doodle/doodlecodingchallenge/slot/dto/CreateSlotRequest.java`
- Create: `src/main/java/com/doodle/doodlecodingchallenge/slot/dto/UpdateSlotRequest.java`
- Create: `src/main/java/com/doodle/doodlecodingchallenge/slot/dto/SlotDto.java`
- Create: `src/main/java/com/doodle/doodlecodingchallenge/slot/SlotRepository.java`
- Create: `src/main/java/com/doodle/doodlecodingchallenge/slot/SlotService.java`
- Create: `src/main/java/com/doodle/doodlecodingchallenge/meeting/Meeting.java` (final version; needed by `Slot`)
- Create: `src/main/java/com/doodle/doodlecodingchallenge/meeting/MeetingParticipant.java` (needed by `Meeting`)
- Test: `src/test/java/com/doodle/doodlecodingchallenge/slot/SlotServiceTest.java`

- [ ] **Step 1: Write the Flyway migration**

`src/main/resources/db/migration/V1__init.sql`:

```sql
CREATE TABLE users (
    id         UUID         PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    email      VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ  NOT NULL
);

CREATE TABLE slots (
    id        UUID         PRIMARY KEY,
    owner_id  UUID         NOT NULL REFERENCES users (id),
    start_at  TIMESTAMPTZ  NOT NULL,
    end_at    TIMESTAMPTZ  NOT NULL,
    status    VARCHAR(16)  NOT NULL,
    version   BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_slots_range CHECK (end_at > start_at),
    CONSTRAINT ck_slots_status CHECK (status IN ('FREE', 'BUSY'))
);

CREATE INDEX idx_slots_owner_start_end ON slots (owner_id, start_at, end_at);

CREATE TABLE meetings (
    id            UUID            PRIMARY KEY,
    title         VARCHAR(255)    NOT NULL,
    description   VARCHAR(2048),
    organizer_id  UUID            NOT NULL REFERENCES users (id),
    slot_id       UUID            NOT NULL UNIQUE REFERENCES slots (id),
    created_at    TIMESTAMPTZ     NOT NULL
);

CREATE INDEX idx_meetings_organizer ON meetings (organizer_id);

CREATE TABLE meeting_participants (
    id           UUID         PRIMARY KEY,
    meeting_id   UUID         NOT NULL REFERENCES meetings (id) ON DELETE CASCADE,
    display_name VARCHAR(255) NOT NULL,
    email        VARCHAR(255) NOT NULL,
    user_id      UUID         REFERENCES users (id),
    CONSTRAINT uq_participant_meeting_email UNIQUE (meeting_id, email)
);

CREATE INDEX idx_participants_email ON meeting_participants (email);
```

(Meeting tables are created here so the schema ships complete in one migration; the entities arrive in Task 5. JPA validation only runs in Task 8, by which time all entities exist.)

- [ ] **Step 2: Write the failing service test**

`src/test/java/com/doodle/doodlecodingchallenge/slot/SlotServiceTest.java`:

```java
package com.doodle.doodlecodingchallenge.slot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import com.doodle.doodlecodingchallenge.common.ConflictException;
import com.doodle.doodlecodingchallenge.common.InvalidRequestException;
import com.doodle.doodlecodingchallenge.common.NotFoundException;
import com.doodle.doodlecodingchallenge.slot.dto.CreateSlotRequest;
import com.doodle.doodlecodingchallenge.slot.dto.SlotDto;
import com.doodle.doodlecodingchallenge.slot.dto.UpdateSlotRequest;
import com.doodle.doodlecodingchallenge.user.User;
import com.doodle.doodlecodingchallenge.user.UserRepository;

@ExtendWith(MockitoExtension.class)
class SlotServiceTest {

    @Mock
    SlotRepository slots;

    @Mock
    UserRepository users;

    SlotService service;
    User owner;
    Instant start = Instant.parse("2026-09-01T10:00:00Z");
    Instant end = Instant.parse("2026-09-01T11:00:00Z");

    @BeforeEach
    void setUp() {
        service = new SlotService(slots, users);
        owner = new User(UUID.randomUUID(), "Alice", "alice@example.com", Instant.now());
    }

    @Test
    void createValidatesRange() {
        assertThatThrownBy(() -> service.create(owner.getId(), new CreateSlotRequest(end, start)))
            .isInstanceOf(InvalidRequestException.class)
            .hasMessageContaining("end must be after start");
    }

    @Test
    void createRejectsUnknownOwner() {
        when(users.findById(owner.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(owner.getId(), new CreateSlotRequest(start, end)))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void createReturnsFreeSlot() {
        when(users.findById(owner.getId())).thenReturn(Optional.of(owner));
        when(slots.save(any(Slot.class))).thenAnswer(inv -> inv.getArgument(0));

        SlotDto dto = service.create(owner.getId(), new CreateSlotRequest(start, end));

        assertThat(dto.status()).isEqualTo(SlotStatus.FREE);
        assertThat(dto.meetingId()).isNull();
        verify(slots).save(any(Slot.class));
    }
    }

    @Test
    void listDelegatesToStatusAwareQueryWhenStatusPresent() {
        when(slots.findByOwnerIdAndStatusAndEndsAtGreaterThanAndStartsAtLessThan(
                any(), any(), any(), any(), any()))
            .thenReturn(Page.empty());

        service.list(owner.getId(), start, end, Optional.of(SlotStatus.BUSY), PageRequest.of(0, 50));

        verify(slots).findByOwnerIdAndStatusAndEndsAtGreaterThanAndStartsAtLessThan(
            org.mockito.ArgumentMatchers.eq(owner.getId()), org.mockito.ArgumentMatchers.eq(SlotStatus.BUSY),
            org.mockito.ArgumentMatchers.eq(start), org.mockito.ArgumentMatchers.eq(end),
            org.mockito.ArgumentMatchers.any(PageRequest.class));
    }

    @Test
    void listDelegatesToPlainQueryWithoutStatus() {
        when(slots.findByOwnerIdAndEndsAtGreaterThanAndStartsAtLessThan(
                any(), any(), any(), any()))
            .thenReturn(Page.empty());

        service.list(owner.getId(), start, end, Optional.empty(), PageRequest.of(0, 50));

        verify(slots).findByOwnerIdAndEndsAtGreaterThanAndStartsAtLessThan(
            org.mockito.ArgumentMatchers.eq(owner.getId()),
            org.mockito.ArgumentMatchers.eq(start),
            org.mockito.ArgumentMatchers.eq(end),
            org.mockito.ArgumentMatchers.any(PageRequest.class));
    }

    @Test
    void reschedulesFreeSlot() {
        Slot slot = new Slot(UUID.randomUUID(), owner, start, end);
        when(slots.findById(slot.getId())).thenReturn(Optional.of(slot));

        SlotDto updated = service.update(slot.getId(),
            new UpdateSlotRequest(start.plusSeconds(3600), end.plusSeconds(3600), null));

        assertThat(updated.start()).isEqualTo(start.plusSeconds(3600));
        assertThat(updated.end()).isEqualTo(end.plusSeconds(3600));
        assertThat(updated.status()).isEqualTo(SlotStatus.FREE);
    }

    @Test
    void rescheduleOfBusySlotRejected() {
        Slot slot = new Slot(UUID.randomUUID(), owner, start, end);
        slot.setStatus(SlotStatus.BUSY);
        when(slots.findById(slot.getId())).thenReturn(Optional.of(slot));

        assertThatThrownBy(() -> service.update(slot.getId(),
            new UpdateSlotRequest(start.plusSeconds(3600), null, null)))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("only free slots can be rescheduled");
    }

    @Test
    void manualBusyThenFreeRoundTrip() {
        Slot slot = new Slot(UUID.randomUUID(), owner, start, end);
        when(slots.findById(slot.getId())).thenReturn(Optional.of(slot));

        service.update(slot.getId(), new UpdateSlotRequest(null, null, SlotStatus.BUSY));
        assertThat(slot.getStatus()).isEqualTo(SlotStatus.BUSY);

        service.update(slot.getId(), new UpdateSlotRequest(null, null, SlotStatus.FREE));
        assertThat(slot.getStatus()).isEqualTo(SlotStatus.FREE);
    }

    @Test
    void deleteFreeSlotSucceeds() {
        Slot slot = new Slot(UUID.randomUUID(), owner, start, end);
        when(slots.findById(slot.getId())).thenReturn(Optional.of(slot));

        service.delete(slot.getId());

        verify(slots).delete(slot);
    }

    @Test
    void deleteBusySlotRejected() {
        Slot slot = new Slot(UUID.randomUUID(), owner, start, end);
        slot.setStatus(SlotStatus.BUSY);
        when(slots.findById(slot.getId())).thenReturn(Optional.of(slot));

        assertThatThrownBy(() -> service.delete(slot.getId()))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("cannot be deleted");
    }
}
```

- [ ] **Step 3: Run to verify failure**

Run: `./gradlew test --tests 'com.doodle.doodlecodingchallenge.slot.SlotServiceTest'`
Expected: FAIL — compile error (Slot/SlotRepository/SlotService/DTOs missing).

- [ ] **Step 4: Implement the slot domain**

`src/main/java/com/doodle/doodlecodingchallenge/slot/SlotStatus.java`:

```java
package com.doodle.doodlecodingchallenge.slot;

public enum SlotStatus {
    FREE, BUSY
}
```

`src/main/java/com/doodle/doodlecodingchallenge/slot/Slot.java`:

```java
package com.doodle.doodlecodingchallenge.slot;

import java.time.Instant;
import java.util.UUID;

import com.doodle.doodlecodingchallenge.meeting.Meeting;
import com.doodle.doodlecodingchallenge.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "slots")
public class Slot {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(name = "start_at", nullable = false)
    private Instant startsAt;

    @Column(name = "end_at", nullable = false)
    private Instant endsAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SlotStatus status;

    @Version
    private long version;

    @OneToOne(fetch = FetchType.LAZY, mappedBy = "slot")
    private Meeting meeting;

    protected Slot() {
    }

    public Slot(UUID id, User owner, Instant startsAt, Instant endsAt) {
        this.id = id;
        this.owner = owner;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.status = SlotStatus.FREE;
    }

    public void setTimes(Instant startsAt, Instant endsAt) {
        this.startsAt = startsAt;
        this.endsAt = endsAt;
    }

    public void setStatus(SlotStatus status) {
        this.status = status;
    }

    public void linkMeeting(Meeting meeting) {
        this.meeting = meeting;
        this.status = SlotStatus.BUSY;
    }

    public void unlinkMeeting() {
        this.meeting = null;
        this.status = SlotStatus.FREE;
    }

    public UUID getId() {
        return id;
    }

    public User getOwner() {
        return owner;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public SlotStatus getStatus() {
        return status;
    }

    public Meeting getMeeting() {
        return meeting;
    }
}
```

Note: `Slot` references `com.doodle.doodlecodingchallenge.meeting.Meeting`, so both meeting entities are created in this task (final versions — Task 5 only adds the repository, DTOs and service). Create `src/main/java/com/doodle/doodlecodingchallenge/meeting/Meeting.java`:

```java
package com.doodle.doodlecodingchallenge.meeting;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.doodle.doodlecodingchallenge.slot.Slot;
import com.doodle.doodlecodingchallenge.user.User;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "meetings")
public class Meeting {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(length = 2048)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organizer_id", nullable = false)
    private User organizer;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "slot_id", nullable = false, unique = true)
    private Slot slot;

    @OneToMany(mappedBy = "meeting", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MeetingParticipant> participants = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Meeting() {
    }

    public Meeting(UUID id, String title, String description, User organizer, Slot slot, Instant createdAt) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.organizer = organizer;
        this.slot = slot;
        this.createdAt = createdAt;
    }

    public void addParticipant(MeetingParticipant participant) {
        participant.setMeeting(this);
        this.participants.add(participant);
    }

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public User getOrganizer() {
        return organizer;
    }

    public Slot getSlot() {
        return slot;
    }

    public List<MeetingParticipant> getParticipants() {
        return participants;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
```

(`MeetingParticipant` is created in this same task, immediately below, so `Slot.meeting` compiles.)

`src/main/java/com/doodle/doodlecodingchallenge/meeting/MeetingParticipant.java`:

```java
package com.doodle.doodlecodingchallenge.meeting;

import java.util.UUID;

import com.doodle.doodlecodingchallenge.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "meeting_participants",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_participant_meeting_email",
        columnNames = {"meeting_id", "email"}))
public class MeetingParticipant {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meeting_id", nullable = false)
    private Meeting meeting;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(nullable = false)
    private String email;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    protected MeetingParticipant() {
    }

    public MeetingParticipant(UUID id, String displayName, String email, User user) {
        this.id = id;
        this.displayName = displayName;
        this.email = email;
        this.user = user;
    }

    void setMeeting(Meeting meeting) {
        this.meeting = meeting;
    }

    public UUID getId() {
        return id;
    }

    public Meeting getMeeting() {
        return meeting;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getEmail() {
        return email;
    }

    public User getUser() {
        return user;
    }
}
```

`src/main/java/com/doodle/doodlecodingchallenge/slot/dto/CreateSlotRequest.java`:

```java
package com.doodle.doodlecodingchallenge.slot.dto;

import java.time.Instant;

import jakarta.validation.constraints.NotNull;

public record CreateSlotRequest(
        @NotNull Instant start,
        @NotNull Instant end) {
}
```

`src/main/java/com/doodle/doodlecodingchallenge/slot/dto/UpdateSlotRequest.java`:

```java
package com.doodle.doodlecodingchallenge.slot.dto;

import java.time.Instant;

import com.doodle.doodlecodingchallenge.slot.SlotStatus;

public record UpdateSlotRequest(
        Instant start,
        Instant end,
        SlotStatus status) {
}
```

`src/main/java/com/doodle/doodlecodingchallenge/slot/dto/SlotDto.java`:

```java
package com.doodle.doodlecodingchallenge.slot.dto;

import java.time.Instant;
import java.util.UUID;

import com.doodle.doodlecodingchallenge.slot.Slot;
import com.doodle.doodlecodingchallenge.slot.SlotStatus;

public record SlotDto(UUID id, UUID ownerId, Instant start, Instant end, SlotStatus status, UUID meetingId) {

    public static SlotDto from(Slot slot) {
        return new SlotDto(slot.getId(), slot.getOwner().getId(),
            slot.getStartsAt(), slot.getEndsAt(), slot.getStatus(),
            slot.getMeeting() == null ? null : slot.getMeeting().getId());
    }
}
```

`src/main/java/com/doodle/doodlecodingchallenge/slot/SlotRepository.java`:

```java
package com.doodle.doodlecodingchallenge.slot;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SlotRepository extends JpaRepository<Slot, UUID> {

    Page<Slot> findByOwnerIdAndEndsAtGreaterThanAndStartsAtLessThan(
        UUID ownerId, Instant from, Instant to, Pageable pageable);

    Page<Slot> findByOwnerIdAndStatusAndEndsAtGreaterThanAndStartsAtLessThan(
        UUID ownerId, SlotStatus status, Instant from, Instant to, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select s from Slot s
        where s.owner.id in :ownerIds
          and s.status = :status
          and s.startsAt < :to
          and s.endsAt > :from
        """)
    List<Slot> findOverlappingForUpdate(@Param("ownerIds") Collection<UUID> ownerIds,
                                        @Param("status") SlotStatus status,
                                        @Param("from") Instant from,
                                        @Param("to") Instant to);

    @Query("""
        select s from Slot s
        left join fetch s.meeting
        where s.owner.id = :ownerId
          and s.startsAt < :to
          and s.endsAt > :from
        """)
    List<Slot> findOverlappingWithMeeting(@Param("ownerId") UUID ownerId,
                                          @Param("from") Instant from,
                                          @Param("to") Instant to);
}
```

`src/main/java/com/doodle/doodlecodingchallenge/slot/SlotService.java`:

```java
package com.doodle.doodlecodingchallenge.slot;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.doodle.doodlecodingchallenge.common.ConflictException;
import com.doodle.doodlecodingchallenge.common.InvalidRequestException;
import com.doodle.doodlecodingchallenge.common.NotFoundException;
import com.doodle.doodlecodingchallenge.slot.dto.CreateSlotRequest;
import com.doodle.doodlecodingchallenge.slot.dto.SlotDto;
import com.doodle.doodlecodingchallenge.slot.dto.UpdateSlotRequest;
import com.doodle.doodlecodingchallenge.user.User;
import com.doodle.doodlecodingchallenge.user.UserRepository;

@Service
public class SlotService {

    private final SlotRepository slots;
    private final UserRepository users;

    public SlotService(SlotRepository slots, UserRepository users) {
        this.slots = slots;
        this.users = users;
    }

    @Transactional
    public SlotDto create(UUID ownerId, CreateSlotRequest request) {
        validateRange(request.start(), request.end());
        User owner = users.findById(ownerId)
            .orElseThrow(() -> NotFoundException.of("User", ownerId));
        Slot slot = new Slot(UUID.randomUUID(), owner, request.start(), request.end());
        return SlotDto.from(slots.save(slot));
    }

    @Transactional(readOnly = true)
    public Page<SlotDto> list(UUID ownerId, Instant from, Instant to,
                              Optional<SlotStatus> status, Pageable pageable) {
        Page<Slot> page = status
            .map(s -> slots.findByOwnerIdAndStatusAndEndsAtGreaterThanAndStartsAtLessThan(
                ownerId, s, from, to, pageable))
            .orElseGet(() -> slots.findByOwnerIdAndEndsAtGreaterThanAndStartsAtLessThan(
                ownerId, from, to, pageable));
        return page.map(SlotDto::from);
    }

    @Transactional(readOnly = true)
    public SlotDto get(UUID slotId) {
        return SlotDto.from(getEntity(slotId));
    }

    @Transactional
    public SlotDto update(UUID slotId, UpdateSlotRequest request) {
        Slot slot = getEntity(slotId);
        if (request.start() != null || request.end() != null) {
            requireFree(slot, "only free slots can be rescheduled");
            Instant newStart = request.start() != null ? request.start() : slot.getStartsAt();
            Instant newEnd = request.end() != null ? request.end() : slot.getEndsAt();
            validateRange(newStart, newEnd);
            slot.setTimes(newStart, newEnd);
        }
        if (request.status() != null) {
            switch (request.status()) {
                case BUSY -> {
                    if (slot.getStatus() == SlotStatus.FREE) {
                        slot.setStatus(SlotStatus.BUSY);
                    }
                }
                case FREE -> {
                    if (slot.getMeeting() != null) {
                        throw new ConflictException(
                            "Slot %s is booked as meeting %s; cancel the meeting to free it"
                                .formatted(slotId, slot.getMeeting().getId()));
                    }
                    if (slot.getStatus() == SlotStatus.BUSY) {
                        slot.setStatus(SlotStatus.FREE);
                    }
                }
            }
        }
        return SlotDto.from(slot);
    }

    @Transactional
    public void delete(UUID slotId) {
        Slot slot = getEntity(slotId);
        if (slot.getStatus() != SlotStatus.FREE) {
            throw new ConflictException(
                "Slot %s is busy and cannot be deleted; free it or cancel its meeting first".formatted(slotId));
        }
        slots.delete(slot);
    }

    private Slot getEntity(UUID slotId) {
        return slots.findById(slotId)
            .orElseThrow(() -> NotFoundException.of("Slot", slotId));
    }

    private static void requireFree(Slot slot, String action) {
        if (slot.getStatus() != SlotStatus.FREE) {
            throw new ConflictException("Slot %s is busy; %s".formatted(slot.getId(), action));
        }
    }

    private static void validateRange(Instant start, Instant end) {
        if (!end.isAfter(start)) {
            throw new InvalidRequestException("end must be after start");
        }
    }
}
```

- [ ] **Step 5: Run to verify pass**

Run: `./gradlew test --tests 'com.doodle.doodlecodingchallenge.slot.SlotServiceTest'`
Expected: PASS (10 tests). (`UpdateSlotRequest` is created earlier in this task.)

- [ ] **Step 6: Commit**

```bash
git add src
git commit -m "feat: time slot schema, entity and management service"
```

---

### Task 5: Meeting booking with conflict detection

**Files:**
- Create: `src/main/java/com/doodle/doodlecodingchallenge/meeting/MeetingRepository.java`
- Create: `src/main/java/com/doodle/doodlecodingchallenge/meeting/dto/ParticipantRequest.java`
- Create: `src/main/java/com/doodle/doodlecodingchallenge/meeting/dto/BookRequest.java`
- Create: `src/main/java/com/doodle/doodlecodingchallenge/meeting/dto/ParticipantDto.java`
- Create: `src/main/java/com/doodle/doodlecodingchallenge/meeting/dto/MeetingDto.java`
- Create: `src/main/java/com/doodle/doodlecodingchallenge/meeting/MeetingService.java`
- Test: `src/test/java/com/doodle/doodlecodingchallenge/meeting/MeetingServiceTest.java`
- Modify: `src/test/java/com/doodle/doodlecodingchallenge/slot/SlotServiceTest.java` (add 2 tests)

(`Meeting.java` and `MeetingParticipant.java` were created in Task 4; this task adds booking logic.)

- [ ] **Step 1: Write the failing booking tests**

`src/test/java/com/doodle/doodlecodingchallenge/meeting/MeetingServiceTest.java`:

```java
package com.doodle.doodlecodingchallenge.meeting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.doodle.doodlecodingchallenge.common.ConflictException;
import com.doodle.doodlecodingchallenge.common.InvalidRequestException;
import com.doodle.doodlecodingchallenge.common.NotFoundException;
import com.doodle.doodlecodingchallenge.meeting.dto.BookRequest;
import com.doodle.doodlecodingchallenge.meeting.dto.MeetingDto;
import com.doodle.doodlecodingchallenge.meeting.dto.ParticipantRequest;
import com.doodle.doodlecodingchallenge.slot.Slot;
import com.doodle.doodlecodingchallenge.slot.SlotRepository;
import com.doodle.doodlecodingchallenge.slot.SlotStatus;
import com.doodle.doodlecodingchallenge.user.User;
import com.doodle.doodlecodingchallenge.user.UserRepository;

@ExtendWith(MockitoExtension.class)
class MeetingServiceTest {

    @Mock
    SlotRepository slots;

    @Mock
    UserRepository users;

    @Mock
    MeetingRepository meetings;

    MeetingService service;

    User alice;
    User bob;
    Slot slot;
    Instant start = Instant.parse("2026-09-01T10:00:00Z");
    Instant end = Instant.parse("2026-09-01T11:00:00Z");

    @BeforeEach
    void setUp() {
        service = new MeetingService(slots, users, meetings);
        alice = new User(UUID.randomUUID(), "Alice", "alice@example.com", Instant.now());
        bob = new User(UUID.randomUUID(), "Bob", "bob@example.com", Instant.now());
        slot = new Slot(UUID.randomUUID(), alice, start, end);
    }

    @Test
    void booksFreeSlotLinksItAndLocksInvolvedUsers() {
        when(slots.findById(slot.getId())).thenReturn(Optional.of(slot));
        when(users.findByEmailIgnoreCase("bob@example.com")).thenReturn(Optional.of(bob));
        when(users.findByEmailIgnoreCase("guest@example.com")).thenReturn(Optional.empty());
        when(slots.findOverlappingForUpdate(any(), any(), any(), any())).thenReturn(List.of());

        MeetingDto dto = service.book(slot.getId(), new BookRequest("Design sync", "weekly",
            List.of(new ParticipantRequest("Bob", "bob@example.com"),
                new ParticipantRequest("Guest", "guest@example.com"))));

        assertThat(slot.getStatus()).isEqualTo(SlotStatus.BUSY);
        assertThat(slot.getMeeting()).isNotNull();
        assertThat(dto.organizerId()).isEqualTo(alice.getId());
        assertThat(dto.participants()).hasSize(2);
        assertThat(dto.participants().get(0).userId()).isEqualTo(bob.getId());
        assertThat(dto.participants().get(1).userId()).isNull();

        ArgumentCaptor<Collection<UUID>> locked = ArgumentCaptor.forClass(Collection.class);
        verify(users).findAllByIdForUpdate(locked.capture());
        assertThat(locked.getValue()).containsExactlyInAnyOrder(alice.getId(), bob.getId());
        verify(slots).findOverlappingForUpdate(eq(locked.getValue()), eq(SlotStatus.BUSY), eq(start), eq(end));
    }

    @Test
    void bookingBusySlotRejected() {
        slot.linkMeeting(new Meeting(UUID.randomUUID(), "Taken", null, alice, slot, Instant.now()));
        when(slots.findById(slot.getId())).thenReturn(Optional.of(slot));

        assertThatThrownBy(() -> service.book(slot.getId(), new BookRequest("X", null,
            List.of(new ParticipantRequest("G", "g@x.com")))))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("not available");
    }

    @Test
    void overlappingBusyTimeOfRegisteredParticipantRejected() {
        when(slots.findById(slot.getId())).thenReturn(Optional.of(slot));
        when(users.findByEmailIgnoreCase("bob@example.com")).thenReturn(Optional.of(bob));
        when(slots.findOverlappingForUpdate(any(), any(), any(), any()))
            .thenReturn(List.of(busySlotOf(bob)));

        assertThatThrownBy(() -> service.book(slot.getId(), new BookRequest("Overlap", null,
            List.of(new ParticipantRequest("Bob", "bob@example.com")))))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("bob@example.com");
    }

    @Test
    void duplicateParticipantEmailRejected() {
        when(slots.findById(slot.getId())).thenReturn(Optional.of(slot));

        assertThatThrownBy(() -> service.book(slot.getId(), new BookRequest("Dup", null,
            List.of(new ParticipantRequest("A", "same@x.com"),
                    new ParticipantRequest("B", "same@x.com")))))
            .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void cancelFreesSlot() {
        Meeting meeting = new Meeting(UUID.randomUUID(), "Standup", null, alice, slot, Instant.now());
        slot.linkMeeting(meeting);
        when(meetings.findByIdWithParticipants(meeting.getId())).thenReturn(Optional.of(meeting));

        service.cancel(meeting.getId());

        assertThat(slot.getStatus()).isEqualTo(SlotStatus.FREE);
        assertThat(slot.getMeeting()).isNull();
        verify(meetings).delete(meeting);
    }

    @Test
    void getUnknownMeetingThrows() {
        when(meetings.findByIdWithParticipants(any(UUID.class))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(UUID.randomUUID()))
            .isInstanceOf(NotFoundException.class);
    }

    private Slot busySlotOf(User busyOwner) {
        Slot s = new Slot(UUID.randomUUID(), busyOwner, start, end);
        s.linkMeeting(new Meeting(UUID.randomUUID(), "Other", null, busyOwner, s, Instant.now()));
        return s;
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew test --tests 'com.doodle.doodlecodingchallenge.meeting.MeetingServiceTest'`
Expected: FAIL — compile error (MeetingRepository/MeetingService/DTOs missing).

- [ ] **Step 3: Implement meeting booking**

`src/main/java/com/doodle/doodlecodingchallenge/meeting/MeetingRepository.java`:

```java
package com.doodle.doodlecodingchallenge.meeting;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MeetingRepository extends JpaRepository<Meeting, UUID> {

    @Query("""
        select distinct m from Meeting m
        join fetch m.participants
        where m.id = :id
        """)
    Optional<Meeting> findByIdWithParticipants(@Param("id") UUID id);

    @Query("""
        select m.id from Meeting m
        join m.participants p
        where lower(p.email) = lower(:email)
        """)
    Page<UUID> findIdsByParticipantEmail(@Param("email") String email, Pageable pageable);

    @Query("""
        select distinct m from Meeting m
        join fetch m.participants
        join fetch m.organizer
        join fetch m.slot
        where m.id in :ids
        """)
    List<Meeting> findAllWithParticipantsById(@Param("ids") Collection<UUID> ids);

    @Query("""
        select distinct m from Meeting m
        join fetch m.slot
        join m.participants p
        where p.user.id = :userId
          and m.slot.startsAt < :to
          and m.slot.endsAt > :from
        """)
    List<Meeting> findMeetingsAttended(@Param("userId") UUID userId,
                                       @Param("from") Instant from,
                                       @Param("to") Instant to);
}
```

DTOs:

`src/main/java/com/doodle/doodlecodingchallenge/meeting/dto/ParticipantRequest.java`:

```java
package com.doodle.doodlecodingchallenge.meeting.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ParticipantRequest(
        @NotBlank String name,
        @NotBlank @Email String email) {
}
```

`src/main/java/com/doodle/doodlecodingchallenge/meeting/dto/BookRequest.java`:

```java
package com.doodle.doodlecodingchallenge.meeting.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record BookRequest(
        @NotBlank String title,
        @Size(max = 2048) String description,
        @NotEmpty List<ParticipantRequest> participants) {
}
```

`src/main/java/com/doodle/doodlecodingchallenge/meeting/dto/ParticipantDto.java`:

```java
package com.doodle.doodlecodingchallenge.meeting.dto;

import java.util.UUID;

import com.doodle.doodlecodingchallenge.meeting.MeetingParticipant;

public record ParticipantDto(String name, String email, UUID userId) {

    public static ParticipantDto from(MeetingParticipant participant) {
        return new ParticipantDto(participant.getDisplayName(), participant.getEmail(),
            participant.getUser() == null ? null : participant.getUser().getId());
    }
}
```

`src/main/java/com/doodle/doodlecodingchallenge/meeting/dto/MeetingDto.java`:

```java
package com.doodle.doodlecodingchallenge.meeting.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.doodle.doodlecodingchallenge.meeting.Meeting;

public record MeetingDto(UUID id, String title, String description, UUID organizerId, UUID slotId,
                         Instant start, Instant end, Instant createdAt,
                         List<ParticipantDto> participants) {

    public static MeetingDto from(Meeting meeting) {
        return new MeetingDto(meeting.getId(), meeting.getTitle(), meeting.getDescription(),
            meeting.getOrganizer().getId(), meeting.getSlot().getId(),
            meeting.getSlot().getStartsAt(), meeting.getSlot().getEndsAt(), meeting.getCreatedAt(),
            meeting.getParticipants().stream().map(ParticipantDto::from).toList());
    }
}
```

`src/main/java/com/doodle/doodlecodingchallenge/meeting/MeetingService.java`:

```java
package com.doodle.doodlecodingchallenge.meeting;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.doodle.doodlecodingchallenge.common.ConflictException;
import com.doodle.doodlecodingchallenge.common.InvalidRequestException;
import com.doodle.doodlecodingchallenge.common.NotFoundException;
import com.doodle.doodlecodingchallenge.meeting.dto.BookRequest;
import com.doodle.doodlecodingchallenge.meeting.dto.MeetingDto;
import com.doodle.doodlecodingchallenge.meeting.dto.ParticipantRequest;
import com.doodle.doodlecodingchallenge.slot.Slot;
import com.doodle.doodlecodingchallenge.slot.SlotRepository;
import com.doodle.doodlecodingchallenge.slot.SlotStatus;
import com.doodle.doodlecodingchallenge.user.UserRepository;

@Service
public class MeetingService {

    private final SlotRepository slots;
    private final UserRepository users;
    private final MeetingRepository meetings;

    public MeetingService(SlotRepository slots, UserRepository users, MeetingRepository meetings) {
        this.slots = slots;
        this.users = users;
        this.meetings = meetings;
    }

    @Transactional
    public MeetingDto book(UUID slotId, BookRequest request) {
        Slot slot = slots.findById(slotId)
            .orElseThrow(() -> NotFoundException.of("Slot", slotId));
        if (slot.getStatus() != SlotStatus.FREE) {
            throw new ConflictException(
                "Slot %s is not available (status %s)".formatted(slotId, slot.getStatus()));
        }

        List<String> emails = request.participants().stream()
            .map(ParticipantRequest::email)
            .toList();
        if (new HashSet<>(emails).size() != emails.size()) {
            throw new InvalidRequestException("Duplicate participant email in request");
        }

        List<MeetingParticipant> participants = request.participants().stream()
            .map(p -> new MeetingParticipant(UUID.randomUUID(), p.name(), p.email(),
                users.findByEmailIgnoreCase(p.email()).orElse(null)))
            .toList();

        TreeSet<UUID> involvedUserIds = new TreeSet<>();
        involvedUserIds.add(slot.getOwner().getId());
        participants.stream()
            .map(MeetingParticipant::getUser)
            .filter(Objects::nonNull)
            .forEach(u -> involvedUserIds.add(u.getId()));

        users.findAllByIdForUpdate(involvedUserIds);

        List<Slot> conflicts = slots.findOverlappingForUpdate(
            involvedUserIds, SlotStatus.BUSY, slot.getStartsAt(), slot.getEndsAt());
        if (!conflicts.isEmpty()) {
            String who = conflicts.stream()
                .map(c -> c.getOwner().getEmail())
                .distinct()
                .collect(Collectors.joining(", "));
            throw new ConflictException("Time range overlaps existing busy time for: " + who);
        }

        Meeting meeting = new Meeting(UUID.randomUUID(), request.title(), request.description(),
            slot.getOwner(), slot, Instant.now());
        participants.forEach(meeting::addParticipant);
        slot.linkMeeting(meeting);
        meetings.save(meeting);
        return MeetingDto.from(meeting);
    }

    @Transactional(readOnly = true)
    public MeetingDto get(UUID id) {
        return meetings.findByIdWithParticipants(id)
            .map(MeetingDto::from)
            .orElseThrow(() -> NotFoundException.of("Meeting", id));
    }

    @Transactional
    public void cancel(UUID id) {
        Meeting meeting = meetings.findByIdWithParticipants(id)
            .orElseThrow(() -> NotFoundException.of("Meeting", id));
        Slot slot = meeting.getSlot();
        slot.unlinkMeeting();
        meetings.delete(meeting);
    }

    @Transactional(readOnly = true)
    public Page<MeetingDto> findByParticipant(String email, Pageable pageable) {
        Page<UUID> ids = meetings.findIdsByParticipantEmail(email, pageable);
        if (ids.isEmpty()) {
            return Page.empty(pageable);
        }
        Map<UUID, Meeting> byId = meetings.findAllWithParticipantsById(ids.getContent())
            .stream()
            .collect(Collectors.toMap(Meeting::getId, m -> m));
        List<MeetingDto> content = ids.getContent().stream()
            .map(byId::get)
            .map(MeetingDto::from)
            .toList();
        return new PageImpl<>(content, ids.getPageable(), ids.getTotalElements());
    }
}
```

(This file needs imports `java.util.Map` and uses `ParticipantDto` from the dto package; adjust the import list to exactly: `java.time.Instant`, `java.util.HashSet`, `java.util.List`, `java.util.Map`, `java.util.Objects`, `java.util.TreeSet`, `java.util.UUID`, `java.util.stream.Collectors`, plus the Spring/doodle imports shown.)

- [ ] **Step 4: Run the booking tests to verify pass**

Run: `./gradlew test --tests 'com.doodle.doodlecodingchallenge.meeting.MeetingServiceTest'`
Expected: PASS (5 tests).

- [ ] **Step 5: Add the two meeting-dependent slot tests**

Append to `src/test/java/com/doodle/doodlecodingchallenge/slot/SlotServiceTest.java` (add import `com.doodle.doodlecodingchallenge.meeting.Meeting`):

```java
    @Test
    void rescheduleRejectedWhenMeetingLinked() {
        Slot slot = new Slot(UUID.randomUUID(), owner, start, end);
        slot.linkMeeting(new Meeting(UUID.randomUUID(), "Standup", null, owner, slot, Instant.now()));
        when(slots.findById(slot.getId())).thenReturn(Optional.of(slot));

        assertThatThrownBy(() -> service.update(slot.getId(),
            new UpdateSlotRequest(start.plusSeconds(3600), null, null)))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("only free slots can be rescheduled");
    }

    @Test
    void markFreeRejectedWhenMeetingLinked() {
        Slot slot = new Slot(UUID.randomUUID(), owner, start, end);
        Meeting meeting = new Meeting(UUID.randomUUID(), "Standup", null, owner, slot, Instant.now());
        slot.linkMeeting(meeting);
        when(slots.findById(slot.getId())).thenReturn(Optional.of(slot));

        assertThatThrownBy(() -> service.update(slot.getId(),
            new UpdateSlotRequest(null, null, SlotStatus.FREE)))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("cancel the meeting");
    }
```

Run: `./gradlew test --tests 'com.doodle.doodlecodingchallenge.slot.SlotServiceTest'`
Expected: PASS (12 tests).

- [ ] **Step 6: Commit**

```bash
git add src
git commit -m "feat: meeting booking with conflict detection"
```

---

### Task 6: Aggregated free/busy calendar view

**Files:**
- Create: `src/main/java/com/doodle/doodlecodingchallenge/calendar/dto/CalendarEntryDto.java`
- Create: `src/main/java/com/doodle/doodlecodingchallenge/calendar/CalendarService.java`
- Test: `src/test/java/com/doodle/doodlecodingchallenge/calendar/CalendarServiceTest.java`

- [ ] **Step 1: Write the failing test**

`src/test/java/com/doodle/doodlecodingchallenge/calendar/CalendarServiceTest.java`:

```java
package com.doodle.doodlecodingchallenge.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.doodle.doodlecodingchallenge.calendar.dto.CalendarEntryDto;
import com.doodle.doodlecodingchallenge.common.NotFoundException;
import com.doodle.doodlecodingchallenge.meeting.Meeting;
import com.doodle.doodlecodingchallenge.meeting.MeetingParticipant;
import com.doodle.doodlecodingchallenge.meeting.MeetingRepository;
import com.doodle.doodlecodingchallenge.slot.Slot;
import com.doodle.doodlecodingchallenge.slot.SlotRepository;
import com.doodle.doodlecodingchallenge.slot.SlotStatus;
import com.doodle.doodlecodingchallenge.user.User;
import com.doodle.doodlecodingchallenge.user.UserRepository;

@ExtendWith(MockitoExtension.class)
class CalendarServiceTest {

    @Mock
    SlotRepository slots;

    @Mock
    UserRepository users;

    @Mock
    MeetingRepository meetings;

    CalendarService service;
    User alice;
    User bob;
    Instant from = Instant.parse("2026-09-01T00:00:00Z");
    Instant to = Instant.parse("2026-09-02T00:00:00Z");

    @BeforeEach
    void setUp() {
        service = new CalendarService(slots, users, meetings);
        alice = new User(UUID.randomUUID(), "Alice", "alice@example.com", Instant.now());
        bob = new User(UUID.randomUUID(), "Bob", "bob@example.com", Instant.now());
    }

    @Test
    void unknownUserThrows() {
        when(users.findById(alice.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.view(alice.getId(), from, to, Optional.empty()))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void busyViewContainsOwnMeetingsManualBusyAndAttendedMeetings() {
        Slot freeSlot = new Slot(UUID.randomUUID(), alice,
            Instant.parse("2026-09-01T09:00:00Z"), Instant.parse("2026-09-01T09:30:00Z"));
        Slot manualBusy = new Slot(UUID.randomUUID(), alice,
            Instant.parse("2026-09-01T12:00:00Z"), Instant.parse("2026-09-01T12:30:00Z"));
        manualBusy.setStatus(SlotStatus.BUSY);
        Slot ownMeetingSlot = new Slot(UUID.randomUUID(), alice,
            Instant.parse("2026-09-01T13:00:00Z"), Instant.parse("2026-09-01T14:00:00Z"));
        Meeting ownMeeting = new Meeting(UUID.randomUUID(), "Sync", null, alice, ownMeetingSlot, Instant.now());
        ownMeetingSlot.linkMeeting(ownMeeting);

        Slot bobsSlot = new Slot(UUID.randomUUID(), bob,
            Instant.parse("2026-09-01T10:00:00Z"), Instant.parse("2026-09-01T11:00:00Z"));
        Meeting attended = new Meeting(UUID.randomUUID(), "Review", null, bob, bobsSlot, Instant.now());
        attended.addParticipant(new MeetingParticipant(UUID.randomUUID(), "Alice", "alice@example.com", alice));
        bobsSlot.linkMeeting(attended);

        when(users.findById(alice.getId())).thenReturn(Optional.of(alice));
        when(slots.findOverlappingWithMeeting(alice.getId(), from, to))
            .thenReturn(List.of(freeSlot, manualBusy, ownMeetingSlot));
        when(meetings.findMeetingsAttended(alice.getId(), from, to)).thenReturn(List.of(attended));

        List<CalendarEntryDto> busy = service.view(alice.getId(), from, to, Optional.of(SlotStatus.BUSY));
        List<CalendarEntryDto> free = service.view(alice.getId(), from, to, Optional.of(SlotStatus.FREE));
        List<CalendarEntryDto> all = service.view(alice.getId(), from, to, Optional.empty());

        assertThat(busy).extracting(CalendarEntryDto::title)
            .containsExactlyInAnyOrder("Sync", "Review", null);
        assertThat(busy).allSatisfy(e -> assertThat(e.status()).isEqualTo(SlotStatus.BUSY));
        assertThat(free).hasSize(1);
        assertThat(free.get(0).meetingId()).isNull();
        assertThat(all).hasSize(4);
        assertThat(all).isSortedAccordingTo(Comparator.comparing(CalendarEntryDto::start));
    }
}
```

Note: the manual busy entry has `title == null` — that is the third element asserted as `null` above.

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew test --tests 'com.doodle.doodlecodingchallenge.calendar.CalendarServiceTest'`
Expected: FAIL — compile error (CalendarService/CalendarEntryDto missing).

- [ ] **Step 3: Implement**

`src/main/java/com/doodle/doodlecodingchallenge/calendar/dto/CalendarEntryDto.java`:

```java
package com.doodle.doodlecodingchallenge.calendar.dto;

import java.time.Instant;
import java.util.UUID;

import com.doodle.doodlecodingchallenge.meeting.Meeting;
import com.doodle.doodlecodingchallenge.slot.Slot;
import com.doodle.doodlecodingchallenge.slot.SlotStatus;

public record CalendarEntryDto(
        UUID slotId,
        Instant start,
        Instant end,
        SlotStatus status,
        UUID meetingId,
        String title) {

    public static CalendarEntryDto from(Slot slot) {
        Meeting meeting = slot.getMeeting();
        return new CalendarEntryDto(slot.getId(), slot.getStartsAt(), slot.getEndsAt(),
            slot.getStatus(),
            meeting == null ? null : meeting.getId(),
            meeting == null ? null : meeting.getTitle());
    }

    public static CalendarEntryDto from(Meeting meeting) {
        return new CalendarEntryDto(meeting.getSlot().getId(),
            meeting.getSlot().getStartsAt(), meeting.getSlot().getEndsAt(),
            SlotStatus.BUSY, meeting.getId(), meeting.getTitle());
    }
}
```

`src/main/java/com/doodle/doodlecodingchallenge/calendar/CalendarService.java`:

```java
package com.doodle.doodlecodingchallenge.calendar;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.doodle.doodlecodingchallenge.calendar.dto.CalendarEntryDto;
import com.doodle.doodlecodingchallenge.common.NotFoundException;
import com.doodle.doodlecodingchallenge.meeting.MeetingRepository;
import com.doodle.doodlecodingchallenge.slot.Slot;
import com.doodle.doodlecodingchallenge.slot.SlotRepository;
import com.doodle.doodlecodingchallenge.slot.SlotStatus;
import com.doodle.doodlecodingchallenge.user.UserRepository;

@Service
public class CalendarService {

    private final SlotRepository slots;
    private final UserRepository users;
    private final MeetingRepository meetings;

    public CalendarService(SlotRepository slots, UserRepository users, MeetingRepository meetings) {
        this.slots = slots;
        this.users = users;
        this.meetings = meetings;
    }

    @Transactional(readOnly = true)
    public List<CalendarEntryDto> view(UUID userId, Instant from, Instant to,
                                       Optional<SlotStatus> status) {
        users.findById(userId).orElseThrow(() -> NotFoundException.of("User", userId));

        List<Slot> own = slots.findOverlappingWithMeeting(userId, from, to);
        List<CalendarEntryDto> entries = new ArrayList<>();
        boolean includeFree = status.isEmpty() || status.get() == SlotStatus.FREE;
        boolean includeBusy = status.isEmpty() || status.get() == SlotStatus.BUSY;

        if (includeFree) {
            own.stream().filter(s -> s.getStatus() == SlotStatus.FREE)
                .forEach(s -> entries.add(CalendarEntryDto.from(s)));
        }
        if (includeBusy) {
            own.stream().filter(s -> s.getStatus() == SlotStatus.BUSY)
                .forEach(s -> entries.add(CalendarEntryDto.from(s)));
            meetings.findMeetingsAttended(userId, from, to).stream()
                .filter(m -> !m.getSlot().getOwner().getId().equals(userId))
                .forEach(m -> entries.add(CalendarEntryDto.from(m)));
        }
        return entries.stream().sorted(Comparator.comparing(CalendarEntryDto::start)).toList();
    }
}
```

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew test --tests 'com.doodle.doodlecodingchallenge.calendar.CalendarServiceTest'`
Expected: PASS (2 tests).

- [ ] **Step 5: Update the spec with the clarified busy-view semantics and commit**

In `docs/superpowers/specs/2026-08-29-doodle-scheduling-service-design.md`, replace the calendar-view rule bullet with:

```markdown
- **Calendar view**: `busy` returns the user's booked meetings (as organizer),
  meetings they attend as registered participants, and manually busy slots in
  the frame; `free` returns their `FREE` slots; omitted `status` returns both.
```

Then run all tests and commit:

./gradlew test --tests 'com.doodle.doodlecodingchallenge.calendar.CalendarServiceTest'
git add src docs
git commit -m "feat: aggregated free/busy calendar view"
```

---

### Task 7: REST controllers (slot, meeting, calendar)

**Files:**
- Create: `src/main/java/com/doodle/doodlecodingchallenge/slot/SlotController.java`
- Create: `src/main/java/com/doodle/doodlecodingchallenge/meeting/MeetingController.java`
- Create: `src/main/java/com/doodle/doodlecodingchallenge/calendar/CalendarController.java`
- Test: `src/test/java/com/doodle/doodlecodingchallenge/slot/SlotControllerTest.java`
- Test: `src/test/java/com/doodle/doodlecodingchallenge/meeting/MeetingControllerTest.java`
- Test: `src/test/java/com/doodle/doodlecodingchallenge/calendar/CalendarControllerTest.java`

Note: `from`/`to` query params are required ISO-8601 instants. Pagination defaults: slots `size=50` sorted by `startsAt`; meetings-by-participant `size=20` sorted `createdAt desc`. If `Pageable` request-param resolution fails in the `@WebMvcTest` slice, annotate the failing test class with `@ImportAutoConfiguration(SpringDataWebAutoConfiguration.class)` (`org.springframework.boot.data.web.autoconfigure.SpringDataWebAutoConfiguration`).

- [ ] **Step 1: Write the failing controller tests**

`src/test/java/com/doodle/doodlecodingchallenge/slot/SlotControllerTest.java`:

```java
package com.doodle.doodlecodingchallenge.slot;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.doodle.doodlecodingchallenge.meeting.MeetingService;
import com.doodle.doodlecodingchallenge.meeting.dto.MeetingDto;
import com.doodle.doodlecodingchallenge.meeting.dto.ParticipantDto;
import com.doodle.doodlecodingchallenge.slot.dto.SlotDto;

@WebMvcTest(SlotController.class)
class SlotControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    SlotService slotService;

    @MockitoBean
    MeetingService meetingService;

    @Test
    void createReturns201WithLocation() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();
        when(slotService.create(any(), any())).thenReturn(new SlotDto(slotId, ownerId,
            Instant.parse("2026-09-01T10:00:00Z"), Instant.parse("2026-09-01T11:00:00Z"),
            SlotStatus.FREE, null));

        mockMvc.perform(post("/api/v1/users/{ownerId}/slots", ownerId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"start\":\"2026-09-01T10:00:00Z\",\"end\":\"2026-09-01T11:00:00Z\"}"))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", "/api/v1/slots/" + slotId))
            .andExpect(jsonPath("$.status").value("FREE"));
    }

    @Test
    void createWithMissingFieldsReturns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/v1/users/{ownerId}/slots", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.length()").value(2));
    }

    @Test
    void listReturnsPagedSlots() throws Exception {
        when(slotService.list(any(), any(), any(), any(), any())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/users/{ownerId}/slots", UUID.randomUUID())
                .param("from", "2026-09-01T00:00:00Z")
                .param("to", "2026-09-02T00:00:00Z"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void bookReturns201WithMeetingLocation() throws Exception {
        UUID slotId = UUID.randomUUID();
        UUID meetingId = UUID.randomUUID();
        when(meetingService.book(any(), any())).thenReturn(new MeetingDto(meetingId, "Sync",
            null, UUID.randomUUID(), slotId,
            Instant.parse("2026-09-01T10:00:00Z"), Instant.parse("2026-09-01T11:00:00Z"),
            Instant.now(), List.of(new ParticipantDto("Bob", "bob@example.com", null))));

        mockMvc.perform(post("/api/v1/slots/{id}/book", slotId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Sync\",\"participants\":[{\"name\":\"Bob\",\"email\":\"bob@example.com\"}]}"))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", "/api/v1/meetings/" + meetingId));
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/v1/slots/" + UUID.randomUUID()))
            .andExpect(status().isNoContent());
    }
}
```

`src/test/java/com/doodle/doodlecodingchallenge/meeting/MeetingControllerTest.java`:

```java
package com.doodle.doodlecodingchallenge.meeting;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.doodle.doodlecodingchallenge.meeting.dto.MeetingDto;
import com.doodle.doodlecodingchallenge.meeting.dto.ParticipantDto;

@WebMvcTest(MeetingController.class)
class MeetingControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    MeetingService meetingService;

    @Test
    void getReturnsMeetingWithParticipants() throws Exception {
        UUID meetingId = UUID.randomUUID();
        when(meetingService.get(meetingId)).thenReturn(new MeetingDto(meetingId, "Sync", null,
            UUID.randomUUID(), UUID.randomUUID(),
            Instant.parse("2026-09-01T10:00:00Z"), Instant.parse("2026-09-01T11:00:00Z"),
            Instant.now(), List.of(new ParticipantDto("Bob", "bob@example.com", null))));

        mockMvc.perform(get("/api/v1/meetings/" + meetingId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Sync"))
            .andExpect(jsonPath("$.participants[0].email").value("bob@example.com"));
    }

    @Test
    void cancelReturns204() throws Exception {
        mockMvc.perform(delete("/api/v1/meetings/" + UUID.randomUUID()))
            .andExpect(status().isNoContent());
    }

    @Test
    void byParticipantReturnsPagedMeetings() throws Exception {
        UUID meetingId = UUID.randomUUID();
        when(meetingService.findByParticipant(any(), any())).thenReturn(
            new PageImpl<>(List.of(new MeetingDto(meetingId, "Sync", null,
                UUID.randomUUID(), UUID.randomUUID(),
                Instant.parse("2026-09-01T10:00:00Z"), Instant.parse("2026-09-01T11:00:00Z"),
                Instant.now(), List.of())), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/meetings").param("participant", "bob@example.com"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].id").value(meetingId.toString()));
    }
}
```

`src/test/java/com/doodle/doodlecodingchallenge/calendar/CalendarControllerTest.java`:

```java
package com.doodle.doodlecodingchallenge.calendar;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.doodle.doodlecodingchallenge.calendar.dto.CalendarEntryDto;
import com.doodle.doodlecodingchallenge.slot.SlotStatus;

@WebMvcTest(CalendarController.class)
class CalendarControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    CalendarService calendarService;

    @Test
    void viewReturnsEntries() throws Exception {
        UUID userId = UUID.randomUUID();
        when(calendarService.view(any(), any(), any(), any())).thenReturn(List.of(
            new CalendarEntryDto(UUID.randomUUID(),
                Instant.parse("2026-09-01T10:00:00Z"), Instant.parse("2026-09-01T11:00:00Z"),
                SlotStatus.BUSY, UUID.randomUUID(), "Sync")));

        mockMvc.perform(get("/api/v1/users/{userId}/calendar", userId)
                .param("from", "2026-09-01T00:00:00Z")
                .param("to", "2026-09-02T00:00:00Z"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].status").value("BUSY"))
            .andExpect(jsonPath("$[0].title").value("Sync"));
    }

    @Test
    void viewWithoutFromReturns400ProblemDetail() throws Exception {
        mockMvc.perform(get("/api/v1/users/{userId}/calendar", UUID.randomUUID())
                .param("to", "2026-09-02T00:00:00Z"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("Invalid request"));
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew test --tests 'com.doodle.doodlecodingchallenge.slot.SlotControllerTest' --tests 'com.doodle.doodlecodingchallenge.meeting.MeetingControllerTest' --tests 'com.doodle.doodlecodingchallenge.calendar.CalendarControllerTest'`
Expected: FAIL — compile error (controllers missing).

- [ ] **Step 3: Implement the controllers**

`src/main/java/com/doodle/doodlecodingchallenge/slot/SlotController.java`:

```java
package com.doodle.doodlecodingchallenge.slot;

import java.net.URI;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.doodle.doodlecodingchallenge.meeting.MeetingService;
import com.doodle.doodlecodingchallenge.meeting.dto.BookRequest;
import com.doodle.doodlecodingchallenge.meeting.dto.MeetingDto;
import com.doodle.doodlecodingchallenge.slot.dto.CreateSlotRequest;
import com.doodle.doodlecodingchallenge.slot.dto.SlotDto;
import com.doodle.doodlecodingchallenge.slot.dto.UpdateSlotRequest;

@RestController
public class SlotController {

    private final SlotService slotService;
    private final MeetingService meetingService;

    public SlotController(SlotService slotService, MeetingService meetingService) {
        this.slotService = slotService;
        this.meetingService = meetingService;
    }

    @PostMapping("/api/v1/users/{userId}/slots")
    ResponseEntity<SlotDto> create(@PathVariable UUID userId,
                                   @Valid @RequestBody CreateSlotRequest request) {
        SlotDto created = slotService.create(userId, request);
        return ResponseEntity.created(URI.create("/api/v1/slots/" + created.id())).body(created);
    }

    @GetMapping("/api/v1/users/{userId}/slots")
    Page<SlotDto> list(@PathVariable UUID userId,
                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                       @RequestParam Optional<SlotStatus> status,
                       @PageableDefault(size = 50, sort = "startsAt") Pageable pageable) {
        return slotService.list(userId, from, to, status, pageable);
    }

    @GetMapping("/api/v1/slots/{slotId}")
    SlotDto get(@PathVariable UUID slotId) {
        return slotService.get(slotId);
    }

    @PatchMapping("/api/v1/slots/{slotId}")
    SlotDto update(@PathVariable UUID slotId, @RequestBody UpdateSlotRequest request) {
        return slotService.update(slotId, request);
    }

    @DeleteMapping("/api/v1/slots/{slotId}")
    ResponseEntity<Void> delete(@PathVariable UUID slotId) {
        slotService.delete(slotId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/v1/slots/{slotId}/book")
    ResponseEntity<MeetingDto> book(@PathVariable UUID slotId,
                                    @Valid @RequestBody BookRequest request) {
        MeetingDto meeting = meetingService.book(slotId, request);
        return ResponseEntity.created(URI.create("/api/v1/meetings/" + meeting.id())).body(meeting);
    }
}
```

`src/main/java/com/doodle/doodlecodingchallenge/meeting/MeetingController.java`:

```java
package com.doodle.doodlecodingchallenge.meeting;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.doodle.doodlecodingchallenge.meeting.dto.MeetingDto;

@RestController
@RequestMapping("/api/v1/meetings")
public class MeetingController {

    private final MeetingService meetingService;

    public MeetingController(MeetingService meetingService) {
        this.meetingService = meetingService;
    }

    @GetMapping("/{id}")
    MeetingDto get(@PathVariable UUID id) {
        return meetingService.get(id);
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> cancel(@PathVariable UUID id) {
        meetingService.cancel(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    Page<MeetingDto> byParticipant(@RequestParam String participant,
                                   @PageableDefault(size = 20, sort = "createdAt",
                                       direction = Sort.Direction.DESC) Pageable pageable) {
        return meetingService.findByParticipant(participant, pageable);
    }
}
```

`src/main/java/com/doodle/doodlecodingchallenge/calendar/CalendarController.java`:

```java
package com.doodle.doodlecodingchallenge.calendar;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.doodle.doodlecodingchallenge.calendar.dto.CalendarEntryDto;
import com.doodle.doodlecodingchallenge.slot.SlotStatus;

@RestController
public class CalendarController {

    private final CalendarService calendarService;

    public CalendarController(CalendarService calendarService) {
        this.calendarService = calendarService;
    }

    @GetMapping("/api/v1/users/{userId}/calendar")
    List<CalendarEntryDto> view(@PathVariable UUID userId,
                                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                @RequestParam Optional<SlotStatus> status) {
        return calendarService.view(userId, from, to, status);
    }
}
```

- [ ] **Step 4: Run the whole unit + slice test suite**

Run: `./gradlew test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src
git commit -m "feat: REST endpoints for slots, meetings and calendar"
```

---

### Task 8: Testcontainers integration suite

**Files:**
- Create: `src/test/java/com/doodle/doodlecodingchallenge/AbstractIntegrationTest.java`
- Create: `src/test/java/com/doodle/doodlecodingchallenge/SchedulingFlowTests.java`

Note: if the `@DynamicPropertySource` import does not resolve under Spring Framework 7, use the compiler-suggested relocated package.

- [ ] **Step 1: Write the integration base**

`src/test/java/com/doodle/doodlecodingchallenge/AbstractIntegrationTest.java`:

```java
package com.doodle.doodlecodingchallenge;

import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Tag("integration")
public abstract class AbstractIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    protected MockMvc mockMvc;
}
```

- [ ] **Step 2: Write the flow tests**

`src/test/java/com/doodle/doodlecodingchallenge/SchedulingFlowTests.java` — the complete, final file:

```java
package com.doodle.doodlecodingchallenge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;

class SchedulingFlowTests extends AbstractIntegrationTest {

    private String registerUser(String name, String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"%s\",\"email\":\"%s\"}".formatted(name, email)))
            .andExpect(status().isCreated())
            .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String createSlot(String ownerId, String start, String end) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/users/{id}/slots", ownerId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"start\":\"%s\",\"end\":\"%s\"}".formatted(start, end)))
            .andExpect(status().isCreated())
            .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private int book(String slotId, String title, String... emails) throws Exception {
        StringBuilder participants = new StringBuilder();
        for (int i = 0; i < emails.length; i++) {
            if (i > 0) {
                participants.append(',');
            }
            participants.append("{\"name\":\"P").append(i).append("\",\"email\":\"")
                .append(emails[i]).append("\"}");
        }
        return mockMvc.perform(post("/api/v1/slots/{id}/book", slotId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"%s\",\"participants\":[%s]}".formatted(title, participants)))
            .andReturn()
            .getResponse()
            .getStatus();
    }

    private int patchSlot(String slotId, String body) throws Exception {
        return mockMvc.perform(patch("/api/v1/slots/" + slotId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andReturn()
            .getResponse()
            .getStatus();
    }

    private int deleteSlot(String slotId) throws Exception {
        return mockMvc.perform(delete("/api/v1/slots/" + slotId))
            .andReturn()
            .getResponse()
            .getStatus();
    }

    @Test
    void fullJourneyBookConflictCancelAndFreeAgain() throws Exception {
        String alice = registerUser("Alice", "alice@example.com");
        String bob = registerUser("Bob", "bob@example.com");
        String slotId = createSlot(alice, "2026-09-01T10:00:00Z", "2026-09-01T11:00:00Z");

        MvcResult booked = mockMvc.perform(post("/api/v1/slots/{id}/book", slotId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"title":"Design sync","description":"weekly",
                     "participants":[{"name":"Bob","email":"bob@example.com"},
                                     {"name":"Guest","email":"guest@example.com"}]}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.participants.length()").value(2))
            .andExpect(jsonPath("$.participants[?(@.email=='bob@example.com')].userId")
                .value(org.hamcrest.Matchers.hasItem(bob)))
            .andReturn();
        String meetingId = JsonPath.read(booked.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(get("/api/v1/slots/" + slotId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("BUSY"))
            .andExpect(jsonPath("$.meetingId").value(meetingId));

        mockMvc.perform(get("/api/v1/users/{id}/calendar", alice)
                .param("from", "2026-09-01T00:00:00Z").param("to", "2026-09-02T00:00:00Z")
                .param("status", "busy"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].meetingId").value(meetingId))
            .andExpect(jsonPath("$[0].title").value("Design sync"));

        mockMvc.perform(get("/api/v1/users/{id}/calendar", bob)
                .param("from", "2026-09-01T00:00:00Z").param("to", "2026-09-02T00:00:00Z")
                .param("status", "busy"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].meetingId").value(meetingId));

        String overlapping = createSlot(alice, "2026-09-01T10:30:00Z", "2026-09-01T11:30:00Z");
        mockMvc.perform(post("/api/v1/slots/{id}/book", overlapping)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Clash\",\"participants\":[{\"name\":\"Bob\",\"email\":\"bob@example.com\"}]}"))
            .andExpect(status().isConflict());

        mockMvc.perform(delete("/api/v1/meetings/" + meetingId))
            .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/slots/" + slotId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("FREE"))
            .andExpect(jsonPath("$.meetingId").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void registeredParticipantConflictRejected() throws Exception {
        String alice = registerUser("Alice3", "alice3@example.com");
        String bob = registerUser("Bob3", "bob3@example.com");

        String bobsSlot = createSlot(bob, "2026-09-02T14:00:00Z", "2026-09-02T15:00:00Z");
        assertThat(patchSlot(bobsSlot, "{\"status\":\"BUSY\"}")).isEqualTo(200);

        String alicesSlot = createSlot(alice, "2026-09-02T14:30:00Z", "2026-09-02T15:30:00Z");
        assertThat(book(alicesSlot, "Overlap Bob", "bob3@example.com")).isEqualTo(409);
    }

    @Test
    void manualBusyBlocksOverlappingBookingForOwner() throws Exception {
        String eve = registerUser("Eve", "eve@example.com");
        String busySlot = createSlot(eve, "2026-09-03T10:00:00Z", "2026-09-03T11:00:00Z");
        assertThat(patchSlot(busySlot, "{\"status\":\"BUSY\"}")).isEqualTo(200);

        String overlapping = createSlot(eve, "2026-09-03T10:30:00Z", "2026-09-03T11:30:00Z");
        assertThat(book(overlapping, "Should clash", "guest@example.com")).isEqualTo(409);
    }

    @Test
    void guestsAreNotConflictChecked() throws Exception {
        String frank = registerUser("Frank", "frank@example.com");
        String frankBusy = createSlot(frank, "2026-09-04T10:00:00Z", "2026-09-04T11:00:00Z");
        assertThat(patchSlot(frankBusy, "{\"status\":\"BUSY\"}")).isEqualTo(200);

        String alice = registerUser("Alice4", "alice4@example.com");
        String alicesSlot = createSlot(alice, "2026-09-04T10:30:00Z", "2026-09-04T11:30:00Z");
        assertThat(book(alicesSlot, "Guests only", "stranger@example.com")).isEqualTo(201);
    }

    @Test
    void slotLifecycleRulesEnforced() throws Exception {
        String grace = registerUser("Grace", "grace@example.com");
        String slotId = createSlot(grace, "2026-09-05T09:00:00Z", "2026-09-05T10:00:00Z");

        assertThat(patchSlot(slotId,
            "{\"start\":\"2026-09-05T09:30:00Z\",\"end\":\"2026-09-05T10:30:00Z\"}")).isEqualTo(200);
        assertThat(patchSlot(slotId, "{\"status\":\"BUSY\"}")).isEqualTo(200);
        assertThat(patchSlot(slotId, "{\"start\":\"2026-09-05T08:00:00Z\",\"end\":\"2026-09-05T09:00:00Z\"}"))
            .isEqualTo(409);
        assertThat(deleteSlot(slotId)).isEqualTo(409);
        assertThat(patchSlot(slotId, "{\"status\":\"FREE\"}")).isEqualTo(200);
        assertThat(deleteSlot(slotId)).isEqualTo(204);
    }

    @Test
    void concurrentOverlappingBookingsExactlyOneSucceeds() throws Exception {
        String henry = registerUser("Henry", "henry@example.com");
        registerUser("Iris", "iris@example.com");
        String slot1 = createSlot(henry, "2026-09-06T10:00:00Z", "2026-09-06T11:00:00Z");
        String slot2 = createSlot(henry, "2026-09-06T10:30:00Z", "2026-09-06T11:30:00Z");

        CountDownLatch go = new CountDownLatch(1);
        Callable<Integer> first = () -> {
            go.await();
            return book(slot1, "Race A", "iris@example.com");
        };
        Callable<Integer> second = () -> {
            go.await();
            return book(slot2, "Race B", "iris@example.com");
        };
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> f1 = pool.submit(first);
            Future<Integer> f2 = pool.submit(second);
            go.countDown();
            List<Integer> statuses = List.of(f1.get(30, TimeUnit.SECONDS), f2.get(30, TimeUnit.SECONDS));
            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        } finally {
            pool.shutdownNow();
        }
    }
}
```

- [ ] **Step 3: Run the integration suite**

Run: `./gradlew test --tests 'com.doodle.doodlecodingchallenge.SchedulingFlowTests'`
Expected: PASS (6 tests). Requires Docker (Testcontainers pulls `postgres:17-alpine`).

- [ ] **Step 4: Commit**

```bash
git add src/test
git commit -m "test: end-to-end scheduling flows with Testcontainers"
```

---

### Task 9: OpenAPI configuration and infrastructure verification

**Files:**
- Create: `src/main/java/com/doodle/doodlecodingchallenge/config/OpenApiConfig.java`
- Test: `src/test/java/com/doodle/doodlecodingchallenge/InfrastructureTests.java`

- [ ] **Step 1: Write the failing verification test**

`src/test/java/com/doodle/doodlecodingchallenge/InfrastructureTests.java`:

```java
package com.doodle.doodlecodingchallenge;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

class InfrastructureTests extends AbstractIntegrationTest {

    @Test
    void apiDocsListAllEndpoints() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paths['/api/v1/slots/{slotId}/book']").exists())
            .andExpect(jsonPath("$.paths['/api/v1/users/{userId}/calendar']").exists())
            .andExpect(jsonPath("$.paths['/api/v1/meetings/{id}']").exists());
    }

    @Test
    void healthIsUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void prometheusEndpointExposesMetrics() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
            .andExpect(status().isOk());
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew test --tests 'com.doodle.doodlecodingchallenge.InfrastructureTests'`
Expected: FAIL — `/v3/api-docs` returns 404 until springdoc is configured (dependency was added in Task 1; the info metadata bean is what this task adds; if the paths test already passes because springdoc auto-configures, proceed — the assertions still validate behavior).

- [ ] **Step 3: Implement the OpenAPI metadata**

`src/main/java/com/doodle/doodlecodingchallenge/config/OpenApiConfig.java`:

```java
package com.doodle.doodlecodingchallenge.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI doodleApi() {
        return new OpenAPI().info(new Info()
            .title("Mini Doodle API")
            .version("v1")
            .description("Meeting scheduling service: time slots, meetings, free/busy calendar views"));
    }
}
```

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew test --tests 'com.doodle.doodlecodingchallenge.InfrastructureTests'`
Expected: PASS (3 tests). Swagger UI is then available at `/swagger-ui.html`.

- [ ] **Step 5: Commit**

```bash
git add src
git commit -m "feat: openapi docs and actuator metrics verification"
```

---

### Task 10: Dockerize with docker-compose

**Files:**
- Create: `Dockerfile`
- Create: `.dockerignore`
- Modify: `compose.yaml` (replace entire file)

- [ ] **Step 1: Write the Dockerfile**

`Dockerfile`:

```dockerfile
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY . .
RUN ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/build/libs/app.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

`.dockerignore`:

```
.git
.gradle
build
.idea
docs
```

- [ ] **Step 2: Replace `compose.yaml`**

```yaml
services:
  app:
    build: .
    ports:
      - "8080:8080"
    environment:
      POSTGRES_URL: jdbc:postgresql://postgres:5432/doodle
      POSTGRES_USER: doodle
      POSTGRES_PASSWORD: doodle
    depends_on:
      postgres:
        condition: service_healthy
  postgres:
    image: postgres:17-alpine
    environment:
      POSTGRES_DB: doodle
      POSTGRES_USER: doodle
      POSTGRES_PASSWORD: doodle
    ports:
      - "5432:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U doodle -d doodle"]
      interval: 5s
      timeout: 5s
      retries: 12
```

- [ ] **Step 3: Smoke-test the full stack**

```bash
docker compose up --build -d
for i in $(seq 1 60); do
  if curl -sf http://localhost:8080/actuator/health > /dev/null; then break; fi
  sleep 3
done
curl -sf http://localhost:8080/actuator/health
```

Expected: `{"status":"UP",...}` (first build downloads Gradle + dependencies; may take several minutes).

Then exercise the API once:

```bash
curl -sf -X POST http://localhost:8080/api/v1/users \
  -H 'Content-Type: application/json' \
  -d '{"name":"Smoke","email":"smoke@example.com"}'
```

Expected: HTTP 201 JSON body with an `id`.

Then verify Swagger UI is served:

```bash
curl -sf -o /dev/null -w '%{http_code}\n' http://localhost:8080/swagger-ui/index.html
```

Expected: `200`.

Tear down:

```bash
docker compose down -v
```

- [ ] **Step 4: Commit**

```bash
git add Dockerfile .dockerignore compose.yaml
git commit -m "build: dockerize service with compose"
```

---

### Task 11: README with usage documentation

**Files:**
- Modify: `README.md` (replace entire file)

- [ ] **Step 1: Write the README**

`README.md`:

```markdown
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

## Run for development

```bash
docker compose up postgres -d   # database only
./gradlew bootRun               # app on :8080, expects localhost:5432
./gradlew test                  # unit + Testcontainers integration tests (Docker required)
```

## API walkthrough (curl)

All times are ISO-8601 with a UTC offset. Times in query strings use
`...Z` format directly.

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

```bash
curl -s -X POST localhost:8080/api/v1/users/<aliceId>/slots \
  -H 'Content-Type: application/json' \
  -d '{"start":"2026-09-01T10:00:00Z","end":"2026-09-01T11:00:00Z"}'
# 201 {"id":"<slotId>","ownerId":"<aliceId>","start":"2026-09-01T10:00:00Z","end":"2026-09-01T11:00:00Z","status":"FREE","meetingId":null}
```

### 3. Query slots in a time frame

```bash
curl -s "localhost:8080/api/v1/users/<aliceId>/slots?from=2026-09-01T00:00:00Z&to=2026-09-02T00:00:00Z&status=FREE&page=0&size=50"
```

### 4. Modify a slot (reschedule / mark busy or free) and delete it

```bash
curl -s -X PATCH localhost:8080/api/v1/slots/<slotId> \
  -H 'Content-Type: application/json' \
  -d '{"start":"2026-09-01T10:30:00Z","end":"2026-09-01T11:30:00Z"}'

curl -s -X PATCH localhost:8080/api/v1/slots/<slotId> \
  -H 'Content-Type: application/json' -d '{"status":"BUSY"}'

# busy slots cannot be deleted:
curl -s -X DELETE localhost:8080/api/v1/slots/<slotId>    # 409
curl -s -X PATCH localhost:8080/api/v1/slots/<slotId> \
  -H 'Content-Type: application/json' -d '{"status":"FREE"}'
curl -s -X DELETE localhost:8080/api/v1/slots/<slotId>    # 204
```

### 5. Book a slot as a meeting

```bash
curl -s -X POST localhost:8080/api/v1/slots/<slotId>/book \
  -H 'Content-Type: application/json' \
  -d '{"title":"Design sync","description":"weekly","participants":[{"name":"Bob","email":"bob@example.com"}]}'
# 201 {"id":"<meetingId>",...,"participants":[{"name":"Bob","email":"bob@example.com","userId":"<bobId>"}]}
# Location: /api/v1/meetings/<meetingId>
```

The slot becomes `BUSY` and links to the meeting. If the range overlaps busy
time of the organizer or any registered participant, the API returns
`409` with a ProblemDetail explaining whose calendar conflicts.

### 6. Free/busy calendar view

```bash
curl -s "localhost:8080/api/v1/users/<aliceId>/calendar?from=2026-09-01T00:00:00Z&to=2026-09-02T00:00:00Z&status=busy"
# [{"slotId":"...","start":"2026-09-01T10:00:00Z","end":"2026-09-01T11:00:00Z","status":"BUSY","meetingId":"...","title":"Design sync"}]
```

`status` may be `free`, `busy`, or omitted for both. A registered user's busy
view includes meetings they organize, meetings they attend as a registered
participant, and manually busy slots. Slots overlapping the frame boundaries
are included.

### 7. Meetings

```bash
curl -s localhost:8080/api/v1/meetings/<meetingId>          # details + participants
curl -s "localhost:8080/api/v1/meetings?participant=bob@example.com"
curl -s -X DELETE localhost:8080/api/v1/meetings/<meetingId> # 204, frees the slot
```

## Error model

All errors use RFC 7807 `application/problem+json`:

| Status | Meaning |
|---|---|
| 400 | Validation failure (field errors) or invalid request (e.g. `end` before `start`) |
| 404 | Unknown user/slot/meeting |
| 409 | Booking overlap, duplicate email, invalid state transition |

## Design notes

- **Concurrency:** booking locks the involved user rows (`SELECT … FOR UPDATE`
  in a stable order) inside the booking transaction, then re-checks an indexed
  overlap query — two concurrent overlapping bookings cannot both succeed; the
  loser gets `409`. Slots also carry an optimistic `@Version`.
- **Performance:** range queries hit the index `(owner_id, start_at, end_at)`;
  the calendar view is a single indexed query per user; participants are
  fetched with join-fetch (no N+1); list endpoints are paginated.
- Schema is managed by Flyway (`src/main/resources/db/migration/V1__init.sql`).
- Design decisions and rejected alternatives: see
  `docs/superpowers/specs/2026-08-29-doodle-scheduling-service-design.md`.
```

- [ ] **Step 2: Run the full test suite one final time**

Run: `./gradlew test`
Expected: PASS (all unit, slice and integration tests).

- [ ] **Step 3: Commit**

```bash
git add README.md
git commit -m "docs: usage and API documentation"
```

---

## Self-review checklist (executed by the implementer before starting)

1. **Spec coverage:** slot CRUD + mark busy/free (Tasks 4, 7), booking + conflicts (Task 5), calendar aggregation (Task 6), persistence/Flyway (Task 4), docker-compose runnable (Task 10), OpenAPI + Swagger (Tasks 1, 9), metrics (Tasks 1, 9), tests (Tasks 2–9).
2. **No placeholders:** every step contains complete code or exact commands.
3. **Type consistency:** `SlotService(SlotRepository, UserRepository)`, `MeetingService(SlotRepository, UserRepository, MeetingRepository)`, `CalendarService(SlotRepository, UserRepository, MeetingRepository)`; entity accessors `getStartsAt()/getEndsAt()/getStatus()/getOwner()/getMeeting()`, mutators `setTimes/setStatus/linkMeeting/unlinkMeeting`; repositories named exactly as called in services (verify with `grep -rn "findOverlappingForUpdate\|findMeetingsAttended\|findAllByIdForUpdate" src/` after implementation).
