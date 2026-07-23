# Connected Appliance Platform — Cisco Assessment
## Slide Outline for PowerPoint / Google Slides

---

### Slide 1 — Title
**Connected Appliance Platform**
*Cisco Take-Home Assessment*
Java 17 · Spring Boot 3.5 · PostgreSQL 17 · Testcontainers

---

### Slide 2 — What Was Built
**Core capability**
- Register vendor-neutral appliances
- Trigger on-demand metric collection (collect-now)
- Normalize vendor-native readings to canonical units
- Persist and query collection history & metric samples
- Paginated, filterable REST APIs

**Technology stack**
- Java 17, Spring Boot 3.5
- PostgreSQL 17, Flyway
- Spring Data JPA, Maven Wrapper 3.9
- JUnit 5, Mockito, Testcontainers

---

### Slide 3 — Architecture: Modular Monolith
**Feature-first packages — each has api / application / domain / infrastructure**

| Module | Responsibility |
|---|---|
| appliance | Registration, metadata, state, interval, next-due scheduling |
| metrics | Collect-now, overlap guard, attempt + sample persistence, history APIs |
| vendor | Adapter SPI, registry, Mock Alpha, Mock Beta, normalization |
| reporting | Custom + daily reports — Tasks 22–27, not yet started |
| shared | Canonical enums, pagination DTOs, error handling |
| bootstrap | ReviewFixturesRunner (opt-in seed data) |

---

### Slide 4 — Data Flow: Collect-Now → Persist → Query
```
POST /actions/collect-now
  → Eligibility check (ACTIVE? not busy?)
  → Acquire per-appliance overlap guard
  → VendorAdapter.collect()              ← outside any DB transaction
  → NativeMetricNormalizer
  → Canonical MetricSamples
  → Finalization transaction
      persist collection_attempt
      persist metric_sample rows
      update appliance state
  → Return CollectionAttemptResponse
```
**Key rule:** Vendor I/O is never inside a database transaction.

---

### Slide 5 — Vendor Abstraction: Mock Alpha vs Mock Beta

| | Mock Alpha (mock-alpha) | Mock Beta (mock-beta) |
|---|---|---|
| Purpose | Pass-through — already canonical units | Conversion — non-standard units |
| Temperature | temp_c → 21.500000 CELSIUS | temperature_f: 71.6°F → 22.000000 CELSIUS |
| Power | power_w → 125.000000 WATT | power_kw: 0.150 kW → 150.000000 WATT |
| Network | None — in-process | None — in-process |

> **Why two?** Together they prove the normalization pipeline works regardless of what unit a vendor reports. Vendor-specific names never enter the common domain.

---

### Slide 6 — Database Schema (2 Flyway migrations)

**V1 — appliance**
- id (UUID PK), display_name, description, vendor_key, external_reference
- collection_state (ACTIVE | PAUSED), collection_interval_seconds
- next_collection_due_at, consecutive_failure_count, last_collection_status
- version (optimistic lock), created_at, updated_at
- Unique: (vendor_key, external_reference)

**V2 — collection_attempt + metric_sample**
- collection_attempt: id, appliance_id, trigger, outcome, started_at, completed_at, sample_count
- collection_warning: id, attempt_id, code, message
- metric_sample: id, appliance_id, attempt_id, metric_name, unit, value (NUMERIC 20,6), observed_at, ingested_at

---

### Slide 7 — What IS Implemented (Tasks 1–20) ✅

**Appliance APIs**
- POST /api/v1/appliances — Register
- GET  /api/v1/appliances/{id} — Get
- GET  /api/v1/appliances — List (paginated, collectionState filter)
- PUT  …/metadata — Update display metadata
- PUT  …/collection-interval — Update interval (5–86400 s)
- PUT  …/collection-state — Pause / Resume

**Metrics APIs**
- POST …/actions/collect-now — Manual collection
- GET  …/collection-attempts — History (paginated, trigger & outcome filter)
- GET  …/metrics — Normalized samples ([from, to) range, paginated)

**Infrastructure**
- GET /actuator/health
- X-Correlation-ID header on every response
- Problem+JSON sanitized error responses
- Optimistic concurrency (version column)
- Two deterministic mock vendor adapters

---

### Slide 8 — What is NOT Implemented (Tasks 21–32) ⏳

**Task 21 — Scheduled Collection**
- Database-backed due-state scheduler
- Spring Scheduler tick; auto-collects ACTIVE appliances when nextCollectionDueAt passes
- Appliances are already being scheduled after collect-now — the coordinator to trigger them is pending

**Tasks 22–27 — Reporting APIs**
- POST /api/v1/reports/custom — custom-range aggregation (max 31 days)
- PUT  /api/v1/reports/daily/{date} — generate/retrieve daily report
- GET  /api/v1/reports/daily — list summaries
- GET  /api/v1/reports/daily/{date} — retrieve one report

**Tasks 28–32 — Polish & Hardening**
- Configurable vendor fault scenarios (PARTIAL_SUCCESS, FAILED, timeouts)
- Springdoc OpenAPI UI
- Docker production image
- Final end-to-end verification

---

### Slide 9 — Live Demo: Postman Collection (37 requests, 7 folders)

| Folder | Requests | What it demonstrates |
|---|---|---|
| 0. Health | 1 | App + DB UP |
| 1. Register | 4 | Registration, 409 duplicate, 422 bad vendor |
| 2. Retrieve | 6 | Get, list, filter ACTIVE/PAUSED, 404 |
| 3. Manage | 6 | Metadata, interval, pause, 404 |
| 4. Collect Now | 6 | 409 paused, resume, Alpha, Beta, 2nd Alpha, 404 |
| 5. Collection History | 6 | All, filter trigger, filter outcome, empty page, 400/404 |
| 6. Metric History | 6 | Alpha samples, Beta unit-conversion proof, error cases |
| 7. Correlation ID | 2 | Valid echo, 400 invalid |

> **Tip:** Run folders 1–4 in order. Folder 1 auto-saves `applianceId` and `applianceBetaId` used by all later requests.

---

### Slide 10 — Optional Reviewer Seed Data

**Activate:** `./mvnw spring-boot:run -Dspring-boot.run.profiles=local,review-fixtures`

| Vendor | Appliances | Attempts | Samples | Notes |
|---|---|---|---|---|
| Mock Alpha | 10 | 20 | 40 | 9 ACTIVE, 1 PAUSED |
| Mock Beta | 10 | 20 | 40 | 9 ACTIVE, 1 PAUSED |
| Total | 20 | 40 | 80 | 0 warnings |

**Enables immediately:**
- Pagination demo: ?page=0&size=5 → 4 pages of results
- State filter: ?collectionState=PAUSED → exactly 2 appliances
- Metric history: samples ready without any manual collect-now
- Normalisation proof: Beta shows 22.000000 CELSIUS & 150.000000 WATT

Idempotent — restarting logs `skipped=20 registered=0` and leaves the DB unchanged.

---

### Slide 11 — Test Coverage: 635 Tests, 0 Failures

| Type | Count | Command |
|---|---|---|
| Unit & MVC | 533 | `./mvnw test` (no Docker) |
| Integration (Testcontainers PostgreSQL) | 102 | `./mvnw verify` |
| **Total** | **635** | **0 failures, 0 errors** |

**Integration test classes (14):**
ApplianceSchemaIT, ApplianceRegistrationIT, ApplianceListingIT, ApplianceMetadataIT,
ApplianceConcurrencyIT, ApplianceRepositoryIT, CollectNowIT, CollectionFinalizationIT,
MetricsSchemaIT, MetricsRepositoryIT, MetricsHistoryIT, ReviewFixturesIT,
PostgresIsolationIT, DatabaseSmokeIT

---

### Slide 12 — Key Design Decisions

| Decision | Reason |
|---|---|
| Vendor calls outside transactions | Prevents DB connection exhaustion under slow vendors |
| Canonical metric model | Historical data is vendor-independent; vendor names never enter common domain |
| Optimistic concurrency (version column) | Concurrent updates → 409 instead of silent last-writer-wins |
| Module boundaries via ports | metrics never imports appliance repositories; cross-module via declared interfaces only |
| Deterministic mock vendors | Stable test values; no flaky external dependencies |
| Optional seed data (profile-gated) | Normal startup is unaffected; reviewer gets rich data on demand |

---

### Slide 13 — How to Run Locally

```bash
# 1. Start PostgreSQL
docker compose up -d postgres

# 2. Start the application (normal)
./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# 2b. With reviewer seed data
./mvnw spring-boot:run -Dspring-boot.run.profiles=local,review-fixtures

# 3. Verify health
curl http://localhost:8080/actuator/health
# → {"status":"UP"}

# Base URL
http://localhost:8080/api/v1

# Run tests
./mvnw test        # no Docker
./mvnw verify      # Docker required
```

Import Postman: `postman/Connected-Appliance-Platform.postman_collection.json`

---

### Slide 14 — Summary

**What was delivered:**
- ✅ 20 of 32 implementation plan tasks complete
- ✅ 9 REST endpoints fully implemented and tested
- ✅ 635 tests passing (0 failures)
- ✅ Mock Alpha + Mock Beta vendor adapters with normalisation
- ✅ 37-request Postman collection covering all implemented APIs
- ✅ Optional reviewer seed data (20 appliances, 40 attempts, 80 samples)
- ⏳ Tasks 21–32 not yet started (scheduler + reporting + polish)

*Thank you — happy to walk through any part of the implementation.*
