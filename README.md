# Connected Appliance Platform

A locally runnable backend that gives clients a consistent way to manage connected appliances,
collect metrics through real or mocked vendor interactions, retain historical data, and generate reports.

## Table of Contents

- [Prerequisites](#prerequisites)
- [How to Run the Service](#how-to-run-the-service)
- [How to Run Tests](#how-to-run-tests)
- [API Overview and Verification Walk-through](#api-overview-and-verification-walk-through)
- [Implemented Capabilities](#implemented-capabilities)
- [Not Yet Implemented](#not-yet-implemented)
- [Assumptions and Non-goals](#assumptions-and-non-goals)
- [Important Design Choices](#important-design-choices)
- [AI Usage Note](#ai-usage-note)

---

## Prerequisites

| Requirement | Version |
|---|---|
| Java | 17 |
| Docker (for PostgreSQL) | any recent version |
| Maven | bundled via `./mvnw` — no local install needed |

---

## How to Run the Service

### 1. Start the local PostgreSQL database

```bash
docker compose up -d postgres
```

This starts `postgres:17.10-alpine3.24` on port `5432` with the following defaults:

| Setting | Value |
|---|---|
| Database | `connected_appliance` |
| Username | `connected_appliance` |
| Password | `connected_appliance_local` |
| Port | `5432` |

### 2. Start the application

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

The application starts on **`http://localhost:8080`**.

### 3. Verify the application is up

```bash
curl -i http://localhost:8080/actuator/health
```

Expected response:

```json
{"status":"UP"}
```

### 4. Stop the application and database

```bash
# Ctrl+C to stop the application, then:
docker compose down
```

---

## How to Run Tests

### Unit and MVC tests — no Docker required

```bash
./mvnw test
```

Runs all JUnit/Mockito unit tests and Spring MVC slice tests. No database or Docker needed.

### Full verification suite — requires Docker

```bash
./mvnw verify
```

Runs the full test suite including Testcontainers PostgreSQL integration tests (`*IT` classes under Maven Failsafe).
Docker must be running. An isolated PostgreSQL container is automatically started and torn down.

### Run a specific integration test

```bash
./mvnw -Dit.test=DatabaseSmokeIT verify
./mvnw -Dit.test=CollectNowIT verify
./mvnw -Dit.test=MetricsHistoryIT verify
```

---

## API Overview and Verification Walk-through

Base URL: `http://localhost:8080/api/v1`

All request and response bodies are `application/json`.
Every response includes an `X-Correlation-ID` header (auto-generated if not supplied).

### Step 1 — Register an appliance

```bash
curl -s -X POST http://localhost:8080/api/v1/appliances \
  -H "Content-Type: application/json" \
  -d '{
    "displayName": "Kitchen Fridge",
    "description": "Demo appliance",
    "vendorKey": "mock-alpha",
    "externalReference": "demo-001",
    "collectionIntervalSeconds": 30
  }' | jq .
```

Supported `vendorKey` values: `mock-alpha`, `mock-beta`.

Save the returned `id` as `APPLIANCE_ID` for subsequent calls.

### Step 2 — Retrieve the appliance

```bash
curl -s http://localhost:8080/api/v1/appliances/$APPLIANCE_ID | jq .
```

### Step 3 — List all appliances

```bash
curl -s "http://localhost:8080/api/v1/appliances?page=0&size=20" | jq .
```

Optional filter by state: `?collectionState=ACTIVE` or `?collectionState=PAUSED`.

### Step 4 — Trigger a manual collection

```bash
curl -s -X POST \
  http://localhost:8080/api/v1/appliances/$APPLIANCE_ID/actions/collect-now | jq .
```

The response includes the persisted `CollectionAttemptResponse` with outcome, sample count, and warnings.
Mock Alpha returns deterministic readings: `21.500000 CELSIUS` and `125.000000 WATT`.
Mock Beta returns converted readings: `22.000000 CELSIUS` and `150.000000 WATT`.

### Step 5 — View collection attempt history

```bash
curl -s "http://localhost:8080/api/v1/appliances/$APPLIANCE_ID/collection-attempts?page=0&size=20" | jq .
```

Optional filters: `?trigger=MANUAL`, `?trigger=SCHEDULED`, `?outcome=SUCCESS`, `?outcome=FAILURE`.

### Step 6 — View normalized metric history

```bash
curl -s "http://localhost:8080/api/v1/appliances/$APPLIANCE_ID/metrics\
?from=2026-07-01T00:00:00Z&to=2026-07-31T23:59:59Z&page=0&size=20" | jq .
```

- `from` and `to` are required, UTC (`Z`), and use `[from, to)` semantics.
- Returns normalized `TEMPERATURE` (CELSIUS) and `POWER` (WATT) samples.

### Step 7 — Appliance management operations

**Update display metadata:**

```bash
curl -s -X PUT \
  http://localhost:8080/api/v1/appliances/$APPLIANCE_ID/metadata \
  -H "Content-Type: application/json" \
  -d '{"displayName": "Kitchen Fridge Updated", "description": "New description"}' | jq .
```

**Update collection interval (5–86400 seconds):**

```bash
curl -s -X PUT \
  http://localhost:8080/api/v1/appliances/$APPLIANCE_ID/collection-interval \
  -H "Content-Type: application/json" \
  -d '{"collectionIntervalSeconds": 60}' | jq .
```

**Pause collection:**

```bash
curl -s -X PUT \
  http://localhost:8080/api/v1/appliances/$APPLIANCE_ID/collection-state \
  -H "Content-Type: application/json" \
  -d '{"collectionState": "PAUSED"}' | jq .
```

**Resume collection:**

```bash
curl -s -X PUT \
  http://localhost:8080/api/v1/appliances/$APPLIANCE_ID/collection-state \
  -H "Content-Type: application/json" \
  -d '{"collectionState": "ACTIVE"}' | jq .
```

### Error behavior

| Scenario | HTTP status | Error code |
|---|---|---|
| Non-existent appliance | `404` | `APPLIANCE_NOT_FOUND` |
| Duplicate `vendorKey + externalReference` | `409` | `DUPLICATE_APPLIANCE` |
| Unsupported vendor key | `422` | `UNSUPPORTED_VENDOR` |
| Collect-now on paused appliance | `409` | `APPLIANCE_PAUSED` |
| Collect-now while already collecting | `409` | `COLLECTION_ALREADY_IN_PROGRESS` |
| Invalid/missing required field | `400` | `VALIDATION_ERROR` |
| `from >= to` in metric query | `400` | `INVALID_TIME_RANGE` |

---

## Implemented Capabilities

The following implementation-plan tasks (1–20 of 32) are complete:

| Area | What works |
|---|---|
| Build and database | Java 17, Spring Boot 3, Maven Wrapper, PostgreSQL via Flyway, health endpoint |
| Error handling | Sanitized `ProblemDetail` responses, `X-Correlation-ID` on every response |
| Vendor abstraction | Canonical metric/unit model, adapter registry, duplicate-key startup guard |
| Mock Alpha | Deterministic `TEMPERATURE` (21.5 °C) and `POWER` (125 W); configurable fault scenarios |
| Mock Beta | Name and unit conversion from Fahrenheit/kilowatts; configurable fault scenarios |
| Appliance management | Register, get, list, update metadata, update interval, pause/resume |
| Manual collection | `POST .../actions/collect-now` — synchronous, persisted, returns attempt response |
| Collection history | Paginated `GET .../collection-attempts` with trigger and outcome filters |
| Metric history | Paginated `GET .../metrics` with UTC `[from, to)` range |
| Concurrency | Per-appliance in-memory overlap guard; capped exponential backoff on failure |
| Testing | 60+ unit, MVC, and Testcontainers PostgreSQL integration tests |

---

## Not Yet Implemented

The following capabilities from the assignment are not yet complete due to AI Tocken Expiry to be honest :

| Capability | Reason |
|---|---|
| **Automatic scheduled collection** | Scheduler coordinator (Task 21) not implemented. Manual collect-now demonstrates the full collection and persistence workflow. |
| **Custom date-range reports** | Aggregation service and reporting API (Tasks 22–23) not implemented. Normalized metric history is queryable and serves as the underlying data source. |
| **Daily reports** | Daily report persistence, API, and scheduler (Tasks 24–27) not implemented. |
| **OpenAPI / Swagger UI** | Springdoc wiring (Task 29) not added. API contract is documented in `docs/API_CONTRACT.md`. |
| **Structured logging and Micrometer** | Observability instrumentation (Task 28) not added. |

The core data collection workflow — register, collect, persist, and inspect history — is fully functional and verifiable end to end.

---

## Assumptions and Non-goals

Full details are in [`docs/REQUIREMENTS.md`](docs/REQUIREMENTS.md). Key points:

- A single locally runnable service and one PostgreSQL database are sufficient for review.
- External appliance vendors are mocked in-process; no real hardware or vendor API keys are required.
- Client authentication and authorization are not implemented. The API is intended for local review only.
- Physical appliance deletion is an explicit non-goal; historical metrics and reports must remain meaningful.
- "Configurable intervals" means per-appliance cadence configurable via the API (5–86400 seconds).
- No production deployment, cloud infrastructure, or real appliance hardware is assumed.
- Report time zones are UTC throughout.

---

## Important Design Choices

Full architecture detail is in [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

**Modular monolith with feature packages.** The application uses one Maven project and one PostgreSQL
database, but code is organized into feature modules (`appliance`, `metrics`, `vendor`, `reporting`,
`shared`). Modules communicate only through declared application/query ports, never through each
other's repositories or JPA entities. This keeps logical boundaries clear and testable without
the operational overhead of microservices.

**Vendor calls outside transactions.** Vendor I/O is performed with no open database transaction.
Only the short finalization step — persisting the attempt, warnings, and samples, and updating
appliance scheduling state — runs inside a transaction. This prevents long-held locks and avoids
transaction timeouts when a vendor is slow.

**Canonical metric model.** Adapter implementations translate native vendor readings (arbitrary
names and units) into a small canonical catalog (`TEMPERATURE/CELSIUS`, `POWER/WATT`) before
returning results. Vendor-specific names and units never enter the common domain. This isolates
normalization logic to adapter code and makes historical data vendor-independent.

**Deterministic mock vendors.** Mock Alpha and Mock Beta return stable, predictable values and
support configurable fault scenarios (`SUCCESS`, `TIMEOUT`, `RATE_LIMITED`, `PARTIAL`,
`INVALID_DATA`, `TRANSIENT`, `UNEXPECTED`) via `application.yml`. No random output, no sleeping.
This makes automated tests fast and reproducible.

**Per-appliance in-memory overlap guard.** A `ConcurrentHashMap`-based guard prevents concurrent
collections for the same appliance without requiring a distributed lock or a database claim column.
Appropriate for one application instance; documented as a single-instance constraint.

**Optimistic locking on the Appliance aggregate.** A `@Version` column prevents lost updates
when concurrent API calls modify the same appliance. Conflicts surface as a retriable error
rather than silent overwrites.

**UTC everywhere.** All timestamps, report boundaries, and scheduled windows use UTC. The
application injects a `java.time.Clock` fixed to UTC, which can be overridden in tests for
deterministic time-dependent behavior.

**Test separation.** Unit and MVC tests (`./mvnw test`) run without Docker and are fast.
Integration tests (`*IT`, `./mvnw verify`) use Testcontainers to spin up an isolated
PostgreSQL instance automatically. No manual test database setup is needed.

---

## AI Usage Note

Copilot-Codex was used throughout this assignment to assist with:

- Drafting and refining the architecture, API contract, data model, and implementation plan documents.
- Generating boilerplate Spring Boot code (controllers, services, DTOs, JPA entities, Flyway migrations).
- Writing unit, MVC, and Testcontainers integration tests.
- Reviewing and refining code for correctness and consistency with the approved design.

All generated code was reviewed for correctness, security (no secrets, no SQL injection, no
exposed internals), and consistency with the documented architecture. The approved documents
(`docs/REQUIREMENTS.md`, `docs/ARCHITECTURE.md`, `docs/API_CONTRACT.md`, `docs/DATA_MODEL.md`,
`docs/IMPLEMENTATION_PLAN.md`) were maintained as authoritative sources throughout development.
AI assistance was constrained to the approved decisions; no undocumented capabilities were introduced.

---

## Authoritative Documentation

| Document | Purpose |
|---|---|
| [`docs/REQUIREMENTS.md`](docs/REQUIREMENTS.md) | Assignment requirements, assumptions, acceptance criteria |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | Technology stack, module boundaries, data flow |
| [`docs/API_CONTRACT.md`](docs/API_CONTRACT.md) | Endpoint paths, DTO contracts, validation rules, HTTP semantics |
| [`docs/DATA_MODEL.md`](docs/DATA_MODEL.md) | Tables, constraints, indexes, transaction boundaries |
| [`docs/IMPLEMENTATION_PLAN.md`](docs/IMPLEMENTATION_PLAN.md) | Task sequence, decisions, testing strategy |
