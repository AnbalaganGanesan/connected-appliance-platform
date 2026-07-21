# Connected Appliance Platform — REST API Contract

## 1. Contract status and scope

The following are approved architecture decisions:

- DTO-only public contracts; JPA entities are never exposed.
- Controllers call application services.
- Scheduled and manual collection share one collection service.
- Custom reports are synchronous and non-persistent.
- Daily reports are persisted and idempotent.
- UTC timestamps and `[start, end)` report ranges.
- No physical deletion, authentication, authorization, or asynchronous report jobs.
- Actuator health, correlation IDs, structured logs, and internal Micrometer instrumentation.

The endpoint paths, DTO fields, validation limits, HTTP mappings and correlation-header behaviour in this document are approved API design decisions.

The local API is intended for reviewer access and is not suitable for public production deployment. No login, password, Basic authentication, token issuance, refresh-token, user, or role APIs are included. A future production deployment would delegate authentication to an external OAuth2/OIDC identity provider, with this backend validating JWT access tokens as a resource server.

## 2. Common API conventions

| Concern | Proposed convention |
|---|---|
| Business API base path | `/api/v1` |
| Versioning | Major version in the URI. Compatible additions remain in `v1`; breaking changes require `v2`. |
| Resource naming | Lowercase plural nouns and kebab-case path segments, such as `collection-attempts`. No trailing slash. |
| Actions | Use resource-oriented endpoints except the explicit synchronous `actions/collect-now` use case. |
| JSON fields | `lowerCamelCase` |
| Enum values | Upper snake case, such as `PARTIAL_SUCCESS` |
| Identifiers | Server-generated lowercase UUIDs. Daily reports use the UTC date as their public natural identifier. |
| Timestamps | ISO-8601 UTC instants ending in `Z`, for example `2026-07-21T10:15:30.123Z`. |
| Dates | ISO `YYYY-MM-DD`, interpreted exclusively in UTC. |
| Business request content | `application/json` when a body exists |
| Business success response | `application/json` |
| Error response | `application/problem+json` |
| Pagination | Zero-based `page`, default `0`; `size`, default `20`, range `1..100`. |
| Unknown JSON fields | Rejected with `400 VALIDATION_ERROR` to make Swagger mistakes visible. |
| CORS | No unrestricted CORS configuration. |
| Authentication | No OpenAPI security scheme and no authentication endpoints. |

### Time-range semantics

- All timestamps must use the `Z` UTC designator.
- Historical metrics and custom reports use `[from, to)`.
- A sample at `from` is included; a sample at `to` is excluded.
- Missing `from` or `to` returns `400 VALIDATION_ERROR`.
- A malformed timestamp or a timestamp not using the required UTC format returns `400 VALIDATION_ERROR`.
- `from >= to` returns `400 INVALID_TIME_RANGE`.
- A syntactically and semantically valid custom-report range longer than 31 days returns `422 REPORT_RANGE_TOO_LARGE`.
- Future portions of an otherwise valid custom range are permitted but contain no samples.
- Historical metric queries are paginated and have no separate duration limit.
- Daily windows run from UTC midnight to the following UTC midnight.

Time-range error examples:

| Input | Result |
|---|---|
| Missing `from` or `to` | `400 VALIDATION_ERROR` |
| `from=2026-07-21T10:00:00` without `Z` | `400 VALIDATION_ERROR` |
| `from=not-a-timestamp` | `400 VALIDATION_ERROR` |
| `from` equal to or later than `to` | `400 INVALID_TIME_RANGE` |
| Valid custom range of 32 days | `422 REPORT_RANGE_TOO_LARGE` |

### Pagination and fixed sorting

Use pagination only for appliances, collection attempts, metric samples, and daily-report summaries.

`PageResponse<T>` fields:

- `items`: array
- `page`: non-negative integer
- `size`: integer
- `totalElements`: non-negative integer
- `totalPages`: non-negative integer

Fixed ordering:

- Appliances: `createdAt` ascending, then `id`.
- Attempts: `startedAt` descending, then `id`.
- Metrics: `observedAt` ascending, then `id`.
- Daily reports: `date` descending.

A page beyond the final page returns `200` with an empty `items` array.

### Empty-result behavior

- Empty lists and metric queries return `200` with `items: []`.
- Empty custom reports return `200` with `aggregates: []` and `totalSampleCount: 0`.
- Daily generation persists an empty report when no samples exist.
- Retrieval of a specific nonexistent resource returns `404`; it never returns an empty object.
- Successful GET operations do not use `204`.

## 3. Correlation-ID contract

Header name: `X-Correlation-ID`.

A valid correlation ID:

- Is exactly one header value.
- Has length `1..64`.
- Matches `[A-Za-z0-9][A-Za-z0-9._-]{0,63}`.
- Contains no spaces, control characters, commas, or line breaks.

Behavior:

- A valid client-supplied value is used unchanged.
- If the header is missing, the server generates a lowercase UUID.
- A blank, malformed, repeated, or excessively long header is rejected with `400 INVALID_CORRELATION_ID`.
- For an invalid supplied value, the server generates a safe UUID for the error path; the invalid value is not placed in logs.
- The effective ID is returned in `X-Correlation-ID` on every business response and health response.
- Every ProblemDetail response includes `correlationId`.
- The effective value is included in structured request logs and removed from per-request logging context after completion.
- Correlation IDs are diagnostic identifiers, not authentication credentials.

Invalid-header example:

```http
GET /api/v1/appliances
X-Correlation-ID: invalid value with spaces
```

```http
HTTP/1.1 400 Bad Request
Content-Type: application/problem+json
X-Correlation-ID: 6dc6ec7e-664f-496f-b4f6-a6b825743ab4
```

```json
{
  "type": "urn:connected-appliance-platform:problem:invalid-correlation-id",
  "title": "Invalid correlation ID",
  "status": 400,
  "detail": "X-Correlation-ID must contain 1 to 64 letters, digits, periods, underscores, or hyphens.",
  "instance": "/api/v1/appliances",
  "code": "INVALID_CORRELATION_ID",
  "correlationId": "6dc6ec7e-664f-496f-b4f6-a6b825743ab4",
  "timestamp": "2026-07-21T10:00:00Z"
}
```

## 4. Public DTOs

### Appliance DTOs

`RegisterApplianceRequest`

| Field | Type | Required | Validation |
|---|---|---:|---|
| `displayName` | string | Yes | Trimmed length `1..100` |
| `description` | string/null | No | Trimmed maximum length `500` |
| `vendorKey` | string | Yes | Length `1..50`; lowercase letters, digits, and hyphens; must resolve to an adapter |
| `externalReference` | string | Yes | Non-blank, maximum `128`; opaque and case-sensitive |
| `collectionIntervalSeconds` | integer | Yes | `5..86400` |

`UpdateApplianceMetadataRequest`

- `displayName`: required; length `1..100`.
- `description`: nullable; maximum `500`. Null or omission clears it.

`UpdateCollectionIntervalRequest`

- `collectionIntervalSeconds`: required integer, `5..86400`.

`UpdateCollectionStateRequest`

- `collectionState`: required; `ACTIVE` or `PAUSED`.

`ApplianceResponse`

- `id`: UUID
- `displayName`: string
- `description`: string/null
- `vendorKey`: string
- `externalReference`: string
- `collectionState`: `ACTIVE` or `PAUSED`
- `collectionIntervalSeconds`: integer
- `nextCollectionDueAt`: UTC timestamp/null
- `consecutiveFailureCount`: non-negative integer
- `lastCollectionStatus`: `NEVER_ATTEMPTED`, `SUCCESS`, `PARTIAL_SUCCESS`, or `FAILED`
- `createdAt`: UTC timestamp
- `updatedAt`: UTC timestamp

Vendor-specific fields are never accepted or returned.

### Collection DTOs

`CollectionAttemptResponse`

- `id`: UUID
- `applianceId`: UUID
- `trigger`: `MANUAL` or `SCHEDULED`
- `outcome`: `SUCCESS`, `PARTIAL_SUCCESS`, or `FAILED`
- `startedAt`: UTC timestamp
- `completedAt`: UTC timestamp
- `sampleCount`: non-negative integer
- `warnings`: array of `CollectionWarningResponse`
- `failure`: nullable `CollectionFailureResponse`
- `nextCollectionDueAt`: UTC timestamp/null after finalization using the latest appliance state

`CollectionWarningResponse`

- `code`: stable machine-readable string
- `message`: sanitized description

`CollectionFailureResponse`

- `category`: `TIMEOUT`, `RATE_LIMITED`, `INVALID_DATA`, `TRANSIENT`, or `UNEXPECTED`
- `message`: sanitized description
- `retryAfterSeconds`: positive integer/null

Raw vendor exceptions, library types, credentials, and payloads are never exposed.

### Metric DTO

`MetricSampleResponse`

- `id`: UUID
- `applianceId`: UUID
- `collectionAttemptId`: UUID
- `metricName`: canonical metric name
- `unit`: canonical unit
- `value`: JSON decimal number
- `observedAt`: UTC timestamp
- `ingestedAt`: UTC timestamp

### Report DTOs

`CustomReportRequest`

- `from`: required UTC timestamp
- `to`: required UTC timestamp

`MetricAggregateResponse`

- `applianceId`: UUID
- `metricName`: canonical metric name
- `unit`: canonical unit
- `sampleCount`: positive integer
- `minimum`: decimal
- `maximum`: decimal
- `average`: decimal

`CustomReportResponse`

- `from`: UTC timestamp
- `to`: UTC timestamp
- `generatedAt`: UTC timestamp
- `totalSampleCount`: non-negative integer
- `aggregates`: array of `MetricAggregateResponse`

`DailyReportResponse`

- `date`: UTC date
- `from`: UTC timestamp
- `to`: UTC timestamp
- `generatedAt`: UTC timestamp
- `totalSampleCount`: non-negative integer
- `aggregates`: array of `MetricAggregateResponse`

`DailyReportSummaryResponse`

- `date`: UTC date
- `from`: UTC timestamp
- `to`: UTC timestamp
- `generatedAt`: UTC timestamp
- `totalSampleCount`: non-negative integer
- `aggregationCount`: non-negative integer

Reports cover all normalized metric samples in the range and group them by appliance and canonical metric. Appliance-specific report filtering is not included in `v1`.

## 5. Sanitized error contract

### Standard ProblemDetail

```json
{
  "type": "urn:connected-appliance-platform:problem:appliance-not-found",
  "title": "Appliance not found",
  "status": 404,
  "detail": "No appliance exists with the supplied identifier.",
  "instance": "/api/v1/appliances/2f1b71b7-71a1-4b6c-9d68-54ed3bc24618",
  "code": "APPLIANCE_NOT_FOUND",
  "correlationId": "review-7f3a2",
  "timestamp": "2026-07-21T10:00:00Z"
}
```

Required fields:

- `type`: stable problem URI
- `title`: short category
- `status`: HTTP status
- `detail`: sanitized explanation
- `instance`: request path
- `code`: stable machine-readable code
- `correlationId`: effective correlation ID
- `timestamp`: UTC timestamp

Responses must not include:

- Stack traces.
- SQL or constraint details.
- Database table, column, sequence, or internal identifier details.
- Internal exception class names.
- Vendor-client library details.
- Credentials, configuration values, or raw vendor payloads.

Documented public resource UUIDs are API identifiers and may appear where relevant.

### Validation errors

Invalid JSON syntax returns `400 MALFORMED_JSON`.

Syntactically valid JSON containing missing, unknown, or invalid fields returns `400 VALIDATION_ERROR`. Invalid UUIDs, invalid query parameters, missing range fields, malformed timestamps, non-UTC timestamps, and DTO constraint failures also return `400 VALIDATION_ERROR`:

```json
{
  "type": "urn:connected-appliance-platform:problem:validation-error",
  "title": "Request validation failed",
  "status": 400,
  "detail": "One or more request values are invalid.",
  "instance": "/api/v1/appliances",
  "code": "VALIDATION_ERROR",
  "correlationId": "review-7f3a2",
  "timestamp": "2026-07-21T10:00:00Z",
  "errors": [
    {
      "field": "collectionIntervalSeconds",
      "code": "OUT_OF_RANGE",
      "message": "must be between 5 and 86400"
    }
  ]
}
```

Validation items contain `field`, `code`, and `message`. Rejected values are not echoed.

Important problem codes:

- `VALIDATION_ERROR`
- `MALFORMED_JSON`
- `INVALID_CORRELATION_ID`
- `APPLIANCE_NOT_FOUND`
- `DUPLICATE_APPLIANCE`
- `UNSUPPORTED_VENDOR`
- `APPLIANCE_PAUSED`
- `COLLECTION_ALREADY_IN_PROGRESS`
- `INVALID_TIME_RANGE`
- `REPORT_RANGE_TOO_LARGE`
- `INVALID_DAILY_REPORT_DATE`
- `DAILY_REPORT_NOT_FOUND`
- `INTERNAL_ERROR`
- `SERVICE_UNAVAILABLE`

Invalid JSON syntax returns `400 MALFORMED_JSON`; syntactically valid JSON containing missing, unknown, or invalid fields returns `400 VALIDATION_ERROR`; a well-formed custom range exceeding its semantic duration limit returns `422`; resource-state conflicts return `409`.

## 6. Business API operations

All examples use:

```http
X-Correlation-ID: review-7f3a2
```

Every response echoes that header.

### 1. Register an appliance

- **Method/path:** `POST /api/v1/appliances`
- **Purpose:** Register a vendor-neutral appliance with collection configuration.
- **Path parameters:** None.
- **Query parameters:** None.
- **Request DTO:** `RegisterApplianceRequest`.
- **Response DTO:** `ApplianceResponse`.
- **Validation:** All registration constraints; `vendorKey + externalReference` must be unique.
- **Success:** `201 Created`; `Location: /api/v1/appliances/{applianceId}`.
- **Errors:** `400` invalid request; `409 DUPLICATE_APPLIANCE`; `422 UNSUPPORTED_VENDOR`.
- **Idempotent:** No. Repetition after success produces `409`.

Request:

```http
POST /api/v1/appliances
Content-Type: application/json
```

```json
{
  "displayName": "Kitchen appliance",
  "description": "Swagger verification appliance",
  "vendorKey": "mock-alpha",
  "externalReference": "demo-001",
  "collectionIntervalSeconds": 30
}
```

Response:

```http
HTTP/1.1 201 Created
Location: /api/v1/appliances/2f1b71b7-71a1-4b6c-9d68-54ed3bc24618
```

```json
{
  "id": "2f1b71b7-71a1-4b6c-9d68-54ed3bc24618",
  "displayName": "Kitchen appliance",
  "description": "Swagger verification appliance",
  "vendorKey": "mock-alpha",
  "externalReference": "demo-001",
  "collectionState": "ACTIVE",
  "collectionIntervalSeconds": 30,
  "nextCollectionDueAt": "2026-07-21T10:00:30Z",
  "consecutiveFailureCount": 0,
  "lastCollectionStatus": "NEVER_ATTEMPTED",
  "createdAt": "2026-07-21T10:00:00Z",
  "updatedAt": "2026-07-21T10:00:00Z"
}
```

New registrations are active. Their first scheduled due time is registration time plus the configured interval, leaving a predictable window for Swagger collect-now verification.

### 2. Get an appliance

- **Method/path:** `GET /api/v1/appliances/{applianceId}`
- **Purpose:** Retrieve common appliance data and latest collection status.
- **Path parameter:** `applianceId`, UUID.
- **Query parameters:** None.
- **Request DTO:** None.
- **Response DTO:** `ApplianceResponse`.
- **Validation:** Canonical UUID.
- **Success:** `200 OK`.
- **Errors:** `400` invalid UUID; `404 APPLIANCE_NOT_FOUND`.
- **Idempotent:** Yes; safe.

Request:

```http
GET /api/v1/appliances/2f1b71b7-71a1-4b6c-9d68-54ed3bc24618
```

Response:

```json
{
  "id": "2f1b71b7-71a1-4b6c-9d68-54ed3bc24618",
  "displayName": "Kitchen appliance",
  "description": "Swagger verification appliance",
  "vendorKey": "mock-alpha",
  "externalReference": "demo-001",
  "collectionState": "ACTIVE",
  "collectionIntervalSeconds": 30,
  "nextCollectionDueAt": "2026-07-21T10:00:30Z",
  "consecutiveFailureCount": 0,
  "lastCollectionStatus": "NEVER_ATTEMPTED",
  "createdAt": "2026-07-21T10:00:00Z",
  "updatedAt": "2026-07-21T10:00:00Z"
}
```

### 3. List appliances

- **Method/path:** `GET /api/v1/appliances`
- **Purpose:** List registered appliances.
- **Path parameters:** None.
- **Query parameters:** `page`, `size`, optional `collectionState`.
- **Request DTO:** None.
- **Response DTO:** `PageResponse<ApplianceResponse>`.
- **Validation:** Pagination bounds and valid state enum.
- **Success:** `200 OK`.
- **Errors:** `400` invalid pagination or state.
- **Idempotent:** Yes; safe.

Request:

```http
GET /api/v1/appliances?page=0&size=20&collectionState=ACTIVE
```

Response:

```json
{
  "items": [
    {
      "id": "2f1b71b7-71a1-4b6c-9d68-54ed3bc24618",
      "displayName": "Kitchen appliance",
      "description": "Swagger verification appliance",
      "vendorKey": "mock-alpha",
      "externalReference": "demo-001",
      "collectionState": "ACTIVE",
      "collectionIntervalSeconds": 30,
      "nextCollectionDueAt": "2026-07-21T10:00:30Z",
      "consecutiveFailureCount": 0,
      "lastCollectionStatus": "NEVER_ATTEMPTED",
      "createdAt": "2026-07-21T10:00:00Z",
      "updatedAt": "2026-07-21T10:00:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

### 4. Update display metadata

- **Method/path:** `PUT /api/v1/appliances/{applianceId}/metadata`
- **Purpose:** Replace common display metadata without changing vendor identity or collection state.
- **Path parameter:** `applianceId`, UUID.
- **Query parameters:** None.
- **Request DTO:** `UpdateApplianceMetadataRequest`.
- **Response DTO:** `ApplianceResponse`.
- **Validation:** Name and description constraints.
- **Success:** `200 OK`.
- **Errors:** `400` invalid request; `404 APPLIANCE_NOT_FOUND`.
- **Idempotent:** Yes. Identical data is a no-op.

Request:

```http
PUT /api/v1/appliances/2f1b71b7-71a1-4b6c-9d68-54ed3bc24618/metadata
Content-Type: application/json
```

```json
{
  "displayName": "Kitchen demo appliance",
  "description": "Updated through Swagger"
}
```

Response:

```json
{
  "id": "2f1b71b7-71a1-4b6c-9d68-54ed3bc24618",
  "displayName": "Kitchen demo appliance",
  "description": "Updated through Swagger",
  "vendorKey": "mock-alpha",
  "externalReference": "demo-001",
  "collectionState": "ACTIVE",
  "collectionIntervalSeconds": 30,
  "nextCollectionDueAt": "2026-07-21T10:00:30Z",
  "consecutiveFailureCount": 0,
  "lastCollectionStatus": "NEVER_ATTEMPTED",
  "createdAt": "2026-07-21T10:00:00Z",
  "updatedAt": "2026-07-21T10:00:10Z"
}
```

### 5. Update collection interval

- **Method/path:** `PUT /api/v1/appliances/{applianceId}/collection-interval`
- **Purpose:** Replace the per-appliance collection interval.
- **Path parameter:** `applianceId`, UUID.
- **Query parameters:** None.
- **Request DTO:** `UpdateCollectionIntervalRequest`.
- **Response DTO:** `ApplianceResponse`.
- **Validation:** Integer `5..86400`.
- **Success:** `200 OK`.
- **Errors:** `400 VALIDATION_ERROR`; `404 APPLIANCE_NOT_FOUND`.
- **Idempotent:** Yes.

Request:

```http
PUT /api/v1/appliances/2f1b71b7-71a1-4b6c-9d68-54ed3bc24618/collection-interval
Content-Type: application/json
```

```json
{
  "collectionIntervalSeconds": 60
}
```

Response:

```json
{
  "id": "2f1b71b7-71a1-4b6c-9d68-54ed3bc24618",
  "displayName": "Kitchen demo appliance",
  "description": "Updated through Swagger",
  "vendorKey": "mock-alpha",
  "externalReference": "demo-001",
  "collectionState": "ACTIVE",
  "collectionIntervalSeconds": 60,
  "nextCollectionDueAt": "2026-07-21T10:02:00Z",
  "consecutiveFailureCount": 0,
  "lastCollectionStatus": "NEVER_ATTEMPTED",
  "createdAt": "2026-07-21T10:00:00Z",
  "updatedAt": "2026-07-21T10:01:00Z"
}
```

Behavior:

- If active and the value changes, schedule the next collection using the new interval.
- If paused, retain `nextCollectionDueAt = null`.
- Repeating the current value does not recalculate the schedule.
- If collection is already running, the interval update is accepted; finalization uses the latest interval.

### 6. Update collection state

- **Method/path:** `PUT /api/v1/appliances/{applianceId}/collection-state`
- **Purpose:** Set the appliance’s collection state to `ACTIVE` or `PAUSED`.
- **Path parameter:** `applianceId`, UUID.
- **Query parameters:** None.
- **Request DTO:** `UpdateCollectionStateRequest`.
- **Response DTO:** `ApplianceResponse`.
- **Validation:** `collectionState` is required and must be `ACTIVE` or `PAUSED`.
- **Success:** `200 OK`.
- **Errors:** `400 VALIDATION_ERROR`; `404 APPLIANCE_NOT_FOUND`.
- **Idempotent:** Yes.

Pause request:

```http
PUT /api/v1/appliances/2f1b71b7-71a1-4b6c-9d68-54ed3bc24618/collection-state
Content-Type: application/json
```

```json
{
  "collectionState": "PAUSED"
}
```

Pause response:

```json
{
  "id": "2f1b71b7-71a1-4b6c-9d68-54ed3bc24618",
  "displayName": "Kitchen demo appliance",
  "description": "Updated through Swagger",
  "vendorKey": "mock-alpha",
  "externalReference": "demo-001",
  "collectionState": "PAUSED",
  "collectionIntervalSeconds": 60,
  "nextCollectionDueAt": null,
  "consecutiveFailureCount": 0,
  "lastCollectionStatus": "SUCCESS",
  "createdAt": "2026-07-21T10:00:00Z",
  "updatedAt": "2026-07-21T10:03:00Z"
}
```

Resume request:

```http
PUT /api/v1/appliances/2f1b71b7-71a1-4b6c-9d68-54ed3bc24618/collection-state
Content-Type: application/json
```

```json
{
  "collectionState": "ACTIVE"
}
```

Resume response:

```json
{
  "id": "2f1b71b7-71a1-4b6c-9d68-54ed3bc24618",
  "displayName": "Kitchen demo appliance",
  "description": "Updated through Swagger",
  "vendorKey": "mock-alpha",
  "externalReference": "demo-001",
  "collectionState": "ACTIVE",
  "collectionIntervalSeconds": 60,
  "nextCollectionDueAt": "2026-07-21T10:04:00Z",
  "consecutiveFailureCount": 0,
  "lastCollectionStatus": "SUCCESS",
  "createdAt": "2026-07-21T10:00:00Z",
  "updatedAt": "2026-07-21T10:04:00Z"
}
```

State behavior:

- Setting `PAUSED` clears `nextCollectionDueAt`.
- A paused appliance cannot begin new scheduled or manual collection attempts.
- Transitioning from `PAUSED` to `ACTIVE` makes the appliance immediately due.
- Repeating the current state is a no-op and does not change `updatedAt` or recalculate the due time.
- Pausing does not cancel an already-running vendor call.
- A completed in-flight attempt and its valid metric samples may still be persisted.
- Collection finalization reads the latest appliance state.
- If the appliance is paused at finalization, `nextCollectionDueAt` remains null.
- Finalization must not reactivate a paused appliance.
- If the interval changed during the vendor call, finalization uses the latest interval when applying the normal or failure-backoff scheduling policy.

### 7. Collect now

- **Method/path:** `POST /api/v1/appliances/{applianceId}/actions/collect-now`
- **Purpose:** Synchronously run the approved manual collection workflow.
- **Path parameter:** `applianceId`, UUID.
- **Query parameters:** None.
- **Request DTO:** None; request body is not accepted.
- **Response DTO:** `CollectionAttemptResponse`.
- **Validation:** Appliance must exist, be active, and not already be collecting.
- **Success:** `200 OK`.
- **Errors:** `400` invalid UUID; `404 APPLIANCE_NOT_FOUND`; `409 APPLIANCE_PAUSED`; `409 COLLECTION_ALREADY_IN_PROGRESS`.
- **Idempotent:** No. Each accepted invocation starts a new attempt.

Request:

```http
POST /api/v1/appliances/2f1b71b7-71a1-4b6c-9d68-54ed3bc24618/actions/collect-now
```

Response:

```json
{
  "id": "8da9a201-85f4-4f8a-b9ec-a49f79f68361",
  "applianceId": "2f1b71b7-71a1-4b6c-9d68-54ed3bc24618",
  "trigger": "MANUAL",
  "outcome": "SUCCESS",
  "startedAt": "2026-07-21T10:01:00Z",
  "completedAt": "2026-07-21T10:01:02Z",
  "sampleCount": 2,
  "warnings": [],
  "failure": null,
  "nextCollectionDueAt": "2026-07-21T10:01:32Z"
}
```

Collection finalization behavior:

- A completed vendor timeout, rate limit, invalid response, or partial response still returns `200` with the persisted attempt outcome.
- Paused or busy precondition failures create no attempt.
- If the appliance is paused after the call begins, the call is not cancelled.
- The completed attempt and valid samples may be persisted while the appliance remains paused.
- The response returns `nextCollectionDueAt: null` when the latest state is paused.
- Finalization cannot overwrite a concurrent pause with `ACTIVE`.
- When active, next-due calculation uses the latest interval rather than the interval captured when the vendor call began.

### 8. Retrieve collection attempts

- **Method/path:** `GET /api/v1/appliances/{applianceId}/collection-attempts`
- **Purpose:** Retrieve persisted completed attempts and inspect collection history.
- **Path parameter:** `applianceId`, UUID.
- **Query parameters:** `page`, `size`, optional `trigger`, optional `outcome`.
- **Request DTO:** None.
- **Response DTO:** `PageResponse<CollectionAttemptResponse>`.
- **Validation:** UUID, pagination, and enum values.
- **Success:** `200 OK`.
- **Errors:** `400 VALIDATION_ERROR`; `404 APPLIANCE_NOT_FOUND`.
- **Idempotent:** Yes; safe.

Request:

```http
GET /api/v1/appliances/2f1b71b7-71a1-4b6c-9d68-54ed3bc24618/collection-attempts?page=0&size=20&trigger=MANUAL
```

Response:

```json
{
  "items": [
    {
      "id": "8da9a201-85f4-4f8a-b9ec-a49f79f68361",
      "applianceId": "2f1b71b7-71a1-4b6c-9d68-54ed3bc24618",
      "trigger": "MANUAL",
      "outcome": "SUCCESS",
      "startedAt": "2026-07-21T10:01:00Z",
      "completedAt": "2026-07-21T10:01:02Z",
      "sampleCount": 2,
      "warnings": [],
      "failure": null,
      "nextCollectionDueAt": "2026-07-21T10:01:32Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

Only completed, persisted attempts appear. The latest summary remains available on `ApplianceResponse`.

### 9. Retrieve historical normalized metrics

- **Method/path:** `GET /api/v1/appliances/{applianceId}/metrics`
- **Purpose:** Retrieve persisted normalized metrics without contacting a vendor.
- **Path parameter:** `applianceId`, UUID.
- **Query parameters:** required `from`, required `to`, `page`, `size`.
- **Request DTO:** None.
- **Response DTO:** `PageResponse<MetricSampleResponse>`.
- **Validation:** UUID, pagination, and the common time-range rules.
- **Success:** `200 OK`.
- **Errors:**
  - `400 VALIDATION_ERROR` when `from` or `to` is missing.
  - `400 VALIDATION_ERROR` for malformed or non-UTC timestamps.
  - `400 INVALID_TIME_RANGE` when `from >= to`.
  - `404 APPLIANCE_NOT_FOUND`.
- **Idempotent:** Yes; safe.

Request:

```http
GET /api/v1/appliances/2f1b71b7-71a1-4b6c-9d68-54ed3bc24618/metrics?from=2026-07-21T10:00:00Z&to=2026-07-21T11:00:00Z&page=0&size=100
```

Response:

```json
{
  "items": [
    {
      "id": "d952b5f7-1117-4204-93f6-e17535ff9f0f",
      "applianceId": "2f1b71b7-71a1-4b6c-9d68-54ed3bc24618",
      "collectionAttemptId": "8da9a201-85f4-4f8a-b9ec-a49f79f68361",
      "metricName": "TEMPERATURE",
      "unit": "CELSIUS",
      "value": 21.5,
      "observedAt": "2026-07-21T10:01:01Z",
      "ingestedAt": "2026-07-21T10:01:02Z"
    },
    {
      "id": "66bc787c-76ba-4f91-a89b-53e8743de4d6",
      "applianceId": "2f1b71b7-71a1-4b6c-9d68-54ed3bc24618",
      "collectionAttemptId": "8da9a201-85f4-4f8a-b9ec-a49f79f68361",
      "metricName": "POWER",
      "unit": "WATT",
      "value": 125.0,
      "observedAt": "2026-07-21T10:01:01Z",
      "ingestedAt": "2026-07-21T10:01:02Z"
    }
  ],
  "page": 0,
  "size": 100,
  "totalElements": 2,
  "totalPages": 1
}
```

An empty valid range returns `200` with an empty page.

### 10. Generate a custom-date-range report

- **Method/path:** `POST /api/v1/reports/custom`
- **Purpose:** Synchronously aggregate normalized history across all appliances.
- **Path parameters:** None.
- **Query parameters:** None.
- **Request DTO:** `CustomReportRequest`.
- **Response DTO:** `CustomReportResponse`.
- **Validation:** Common UTC time-range rules and maximum duration of 31 days.
- **Success:** `200 OK`.
- **Errors:**
  - `400 VALIDATION_ERROR` when `from` or `to` is missing.
  - `400 VALIDATION_ERROR` for malformed or non-UTC timestamps.
  - `400 INVALID_TIME_RANGE` when `from >= to`.
  - `422 REPORT_RANGE_TOO_LARGE` for an otherwise valid range exceeding 31 days.
- **Idempotency:** The operation does not persist a custom report or create server-side report state. Repeating it has no additional server-side state effect, but it is not representation-idempotent: newly stored samples may change the aggregates, and `generatedAt` changes.

Request:

```http
POST /api/v1/reports/custom
Content-Type: application/json
```

```json
{
  "from": "2026-07-21T10:00:00Z",
  "to": "2026-07-21T11:00:00Z"
}
```

Response:

```json
{
  "from": "2026-07-21T10:00:00Z",
  "to": "2026-07-21T11:00:00Z",
  "generatedAt": "2026-07-21T10:05:00Z",
  "totalSampleCount": 2,
  "aggregates": [
    {
      "applianceId": "2f1b71b7-71a1-4b6c-9d68-54ed3bc24618",
      "metricName": "POWER",
      "unit": "WATT",
      "sampleCount": 1,
      "minimum": 125.0,
      "maximum": 125.0,
      "average": 125.0
    },
    {
      "applianceId": "2f1b71b7-71a1-4b6c-9d68-54ed3bc24618",
      "metricName": "TEMPERATURE",
      "unit": "CELSIUS",
      "sampleCount": 1,
      "minimum": 21.5,
      "maximum": 21.5,
      "average": 21.5
    }
  ]
}
```

### 11. Explicitly generate a daily report

- **Method/path:** `PUT /api/v1/reports/daily/{date}`
- **Purpose:** Idempotently generate or retrieve one persisted UTC daily report.
- **Path parameter:** `date`, `YYYY-MM-DD`.
- **Query parameters:** None.
- **Request DTO:** None.
- **Response DTO:** `DailyReportResponse`.
- **Validation:** The date must represent a completed UTC day; current and future dates return `422`.
- **Success:** `201 Created` when first generated; `200 OK` when already present.
- **Errors:** `400 VALIDATION_ERROR` for malformed date; `422 INVALID_DAILY_REPORT_DATE`.
- **Idempotent:** Yes.

Request:

```http
PUT /api/v1/reports/daily/2026-07-20
```

First response:

```http
HTTP/1.1 201 Created
Location: /api/v1/reports/daily/2026-07-20
```

```json
{
  "date": "2026-07-20",
  "from": "2026-07-20T00:00:00Z",
  "to": "2026-07-21T00:00:00Z",
  "generatedAt": "2026-07-21T10:06:00Z",
  "totalSampleCount": 0,
  "aggregates": []
}
```

An identical repeated request returns `200` with the exact stored representation. Empty daily reports are valid and persisted.

### 12. List generated daily reports

- **Method/path:** `GET /api/v1/reports/daily`
- **Purpose:** List persisted daily-report summaries.
- **Path parameters:** None.
- **Query parameters:** `page`, `size`.
- **Request DTO:** None.
- **Response DTO:** `PageResponse<DailyReportSummaryResponse>`.
- **Validation:** Pagination bounds.
- **Success:** `200 OK`.
- **Errors:** `400 VALIDATION_ERROR`.
- **Idempotent:** Yes; safe.

Request:

```http
GET /api/v1/reports/daily?page=0&size=20
```

Response:

```json
{
  "items": [
    {
      "date": "2026-07-20",
      "from": "2026-07-20T00:00:00Z",
      "to": "2026-07-21T00:00:00Z",
      "generatedAt": "2026-07-21T10:06:00Z",
      "totalSampleCount": 0,
      "aggregationCount": 0
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

### 13. Retrieve a generated daily report

- **Method/path:** `GET /api/v1/reports/daily/{date}`
- **Purpose:** Retrieve a persisted daily report by UTC date.
- **Path parameter:** `date`, `YYYY-MM-DD`.
- **Query parameters:** None.
- **Request DTO:** None.
- **Response DTO:** `DailyReportResponse`.
- **Validation:** Valid date syntax.
- **Success:** `200 OK`.
- **Errors:** `400 VALIDATION_ERROR`; `404 DAILY_REPORT_NOT_FOUND`.
- **Idempotent:** Yes; safe.

Request:

```http
GET /api/v1/reports/daily/2026-07-20
```

Response:

```json
{
  "date": "2026-07-20",
  "from": "2026-07-20T00:00:00Z",
  "to": "2026-07-21T00:00:00Z",
  "generatedAt": "2026-07-21T10:06:00Z",
  "totalSampleCount": 0,
  "aggregates": []
}
```

## 7. Actuator health contract

The health endpoint is operational, not part of `/api/v1`.

- **Method/path:** `GET /actuator/health`
- **Purpose:** Verify application and database availability.
- **Parameters/body:** None.
- **Success:** `200 OK` when aggregate health is `UP`.
- **Unavailable:** `503 Service Unavailable` when dependencies, including PostgreSQL, make health `DOWN`.
- **Idempotent:** Yes; safe.
- **Correlation:** Uses and returns `X-Correlation-ID`.
- **Exposure:** Only `health` is exposed through Actuator’s web interface.
- **Details:** Component status may identify `db` as `UP` or `DOWN`, but connection URLs, credentials, database product details, and exception information are hidden.

Request:

```http
GET /actuator/health
X-Correlation-ID: review-health-1
```

Response:

```http
HTTP/1.1 200 OK
Content-Type: application/vnd.spring-boot.actuator.v3+json
X-Correlation-ID: review-health-1
```

```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP"
    },
    "ping": {
      "status": "UP"
    }
  }
}
```

No Prometheus, tracing, environment, beans, configprops, heapdump, loggers, mappings, scheduled-tasks, metrics, or other management endpoint is publicly exposed. Internal Micrometer counters and timers do not create business endpoints.

## 8. Important HTTP behavior

| Scenario | Proposed result |
|---|---|
| Duplicate vendor key and external reference | `409 DUPLICATE_APPLIANCE`; no second appliance created |
| Unsupported but well-formed vendor key | `422 UNSUPPORTED_VENDOR` |
| Appliance not found | `404 APPLIANCE_NOT_FOUND` |
| Paused collect-now | `409 APPLIANCE_PAUSED`; no attempt created |
| Overlapping collect-now | `409 COLLECTION_ALREADY_IN_PROGRESS`; no second attempt created |
| Invalid collection interval | `400 VALIDATION_ERROR` |
| Invalid JSON syntax | `400 MALFORMED_JSON` |
| Syntactically valid JSON with missing, unknown, or invalid fields | `400 VALIDATION_ERROR` |
| Missing custom-report or metric `from`/`to` | `400 VALIDATION_ERROR` |
| Malformed or non-UTC range timestamp | `400 VALIDATION_ERROR` |
| `from >= to` | `400 INVALID_TIME_RANGE` |
| Valid custom-report range over 31 days | `422 REPORT_RANGE_TOO_LARGE` |
| Empty metric range | `200` with empty page |
| Empty custom report | `200` with zero samples and empty aggregates |
| First daily generation | `201` and persisted result |
| Repeated daily generation | `200` with exact stored result |
| Pause during vendor call | Call may finish; attempt and valid samples may persist; next due remains null |
| Interval change during vendor call | Finalization uses the latest interval |
| Vendor timeout | `200` attempt with `FAILED`, category `TIMEOUT` |
| Vendor rate limit | `200` attempt with `FAILED`, category `RATE_LIMITED`, optional retry delay |
| Partial vendor response | `200` attempt with `PARTIAL_SUCCESS`, valid samples persisted, warnings returned |
| Unexpected vendor failure after invocation | `200` persisted failed attempt with category `UNEXPECTED` and sanitized message |
| Failure before an attempt can be persisted | Sanitized `500 INTERNAL_ERROR` or `503 SERVICE_UNAVAILABLE` |
| Database unavailable | Business API returns sanitized `503`; health returns `503` with `DOWN` |
| Unsupported media type | `415 Unsupported Media Type` |
| Unsupported response media type | `406 Not Acceptable` |

Vendor outcomes use `200` because the platform successfully executed and persisted the collection attempt. Returning `429`, `502`, or `504` would incorrectly imply that the platform request itself was not handled.

## 9. OpenAPI and Swagger presentation

Springdoc should present:

- `Appliances`: registration, retrieval, metadata, interval, and collection state.
- `Metric Collection`: collect-now and attempts.
- `Metrics`: normalized history.
- `Reports`: custom and daily reports.
- `Health`: the unversioned Actuator health endpoint.

OpenAPI requirements:

- Stable operation IDs.
- Descriptions that distinguish approved requirements from API choices.
- Request and response examples from this contract.
- Bean-validation constraints rendered in schemas.
- Every documented success and important error response.
- Reusable ProblemDetail and paginated schemas.
- Optional `X-Correlation-ID` request header and required response header.
- Explicit `[from, to)` descriptions and consistent range-error mappings.
- `application/problem+json` error media types.
- No authentication scheme.
- No vendor-specific registration fields.
- No internal Actuator endpoints other than health.

Suggested documentation locations:

- OpenAPI JSON: `/v3/api-docs`
- Swagger UI: `/swagger-ui.html`
- Health: `/actuator/health`, documented separately from `/api/v1`

## 10. Reviewer-oriented Swagger sequence

1. Check health:

```http
GET /actuator/health
X-Correlation-ID: swagger-review
```

Verify `200`, aggregate `UP`, and database component `UP`.

2. Register `mock-alpha` using the registration example and copy `applianceId`.

3. Retrieve it:

```http
GET /api/v1/appliances/{applianceId}
```

4. Collect now:

```http
POST /api/v1/appliances/{applianceId}/actions/collect-now
```

Copy the returned attempt ID and timestamps.

5. Verify the persisted attempt:

```http
GET /api/v1/appliances/{applianceId}/collection-attempts?trigger=MANUAL&page=0&size=20
```

Confirm the page contains the copied attempt ID.

6. Retrieve metrics using a UTC range surrounding `observedAt`:

```http
GET /api/v1/appliances/{applianceId}/metrics?from={fromUtc}&to={toUtc}&page=0&size=100
```

7. Update cadence:

```http
PUT /api/v1/appliances/{applianceId}/collection-interval
```

```json
{
  "collectionIntervalSeconds": 60
}
```

8. Pause and resume through the same operation.

Pause:

```http
PUT /api/v1/appliances/{applianceId}/collection-state
```

```json
{
  "collectionState": "PAUSED"
}
```

Verify collect-now returns `409 APPLIANCE_PAUSED`.

Resume:

```http
PUT /api/v1/appliances/{applianceId}/collection-state
```

```json
{
  "collectionState": "ACTIVE"
}
```

9. Generate a custom report using the same metric range and verify aggregate count and canonical units.

10. Generate a daily report for a completed UTC date:

```http
PUT /api/v1/reports/daily/{completedUtcDate}
```

Verify `201`, repeat it, and verify `200` with the identical stored representation.

11. Retrieve the stored report:

```http
GET /api/v1/reports/daily/{completedUtcDate}
```

A prior-day fixture or controlled test clock is needed for an immediately non-empty daily Swagger example. This is a verification-data concern, not an additional API.

## 11. API decisions and trade-offs

| Decision | Recommended option | Alternatives | Trade-off |
|---|---|---|---|
| Versioning | `/api/v1` | Header/media-type versioning | Visible and easy in Swagger; breaking versions require new routes. |
| Collection interval | Integer seconds | ISO-8601 duration | Easier to enter and validate; less expressive. |
| Metadata update | Idempotent `PUT` subresource | General appliance `PATCH` | Clear field ownership; adds a focused endpoint. |
| Collection state | One desired-state `PUT` accepting `ACTIVE` or `PAUSED` | Separate pause and resume actions | Naturally idempotent and prevents duplicated transition contracts. |
| Concurrent collection changes | Finalization uses latest state and interval | Snapshot state at collection start; cancellation APIs | Preserves concurrent pause and interval changes without ETags or cancellation complexity. |
| Collect now | Synchronous action endpoint | Asynchronous job | Reviewer-friendly; request may wait for vendor timeout. |
| Vendor failure | Successful HTTP response with failed attempt | `429`, `502`, or `504` | Preserves the attempt as a business outcome; clients must inspect `outcome`. |
| Unsupported vendor | `422` | `400` | Separates semantic rejection from malformed input. |
| Pagination | Offset pagination | Cursor pagination | Simple and sufficient locally; less scalable for large changing datasets. |
| Custom reports | Synchronous, non-persistent `POST` with no additional state effect on repetition | GET query or asynchronous job | No job lifecycle, but responses are not stable because history and `generatedAt` may change. |
| Custom range limit | 31 days | No limit or configurable limit | Protects synchronous execution; the exact limit is a new API assumption. |
| Daily reports | `PUT` keyed by UTC date | POST with generated UUID | Strong idempotency and straightforward retrieval. |
| Current-day daily reports | Reject | Persist a partial immutable day | Protects correctness; prior-day data is needed for a meaningful Swagger example. |
| Report scope | All appliances | Optional appliance filters | Minimal API; filtering can be added compatibly later. |
| Correlation IDs | Accept strict 64-character client IDs or generate UUIDs | Silently replace invalid values | Prevents log injection and makes invalid usage visible. |
| Concurrency control | No ETags | `If-Match` optimistic concurrency | Simpler review; collection finalization explicitly reloads latest appliance state. |
| Health visibility | Aggregate and component status without details | Full Actuator details | Confirms database availability without leaking configuration. |
| Management surface | Health only | Metrics/tracing/environment endpoints | Meets local-review needs without production monitoring infrastructure. |

## 12. Contract verification scenarios

- Valid and missing correlation IDs are echoed; invalid IDs return sanitized `400` with a generated safe ID.
- Registration succeeds for both mock adapters.
- Duplicate registration returns `409`.
- Unsupported vendor returns `422`.
- Invalid JSON syntax returns `400 MALFORMED_JSON`.
- Syntactically valid JSON containing missing, unknown, or invalid fields returns `400 VALIDATION_ERROR`.
- Invalid UUIDs, pagination, intervals, timestamps, and dates return structured validation problems.
- Missing range boundaries return `400 VALIDATION_ERROR`.
- Malformed or non-UTC range timestamps return `400 VALIDATION_ERROR`.
- Equal or reversed ranges return `400 INVALID_TIME_RANGE`.
- Valid custom-report ranges over 31 days return `422 REPORT_RANGE_TOO_LARGE`.
- Response problems contain no stack, SQL, exception-class, vendor-client, or configuration details.
- Repeated metadata, interval, and collection-state requests are idempotent no-ops.
- Paused and overlapping collect-now calls return `409` and create no attempt.
- Pausing during an active vendor call does not cancel it; valid samples may persist, the appliance remains paused, and next due remains null.
- An interval changed during collection is used when finalization calculates the next due time.
- Vendor success, partial response, timeout, rate limit, and unexpected failure create inspectable attempts.
- Metric and report ranges enforce `[from, to)`.
- Empty lists and reports return successful empty representations.
- Repeated custom-report requests create no report state, but their response bodies may change.
- Custom reports do not appear in daily-report listings.
- Daily generation returns `201` once and `200` thereafter with the same result.
- Health returns `200/UP` with PostgreSQL available and `503/DOWN` when unavailable.
- Only the Actuator health endpoint is exposed.
- OpenAPI contains no security schemes or vendor-specific request fields.

## 13. Approved API design assumptions

These are approved API design decisions for v1. They are not original assignment requirements or previously approved architecture decisions:

- Business routes use `/api/v1`.
- Mock adapter keys are `mock-alpha` and `mock-beta`.
- Registration includes optional common `description`.
- Collection intervals range from 5 seconds to 24 hours.
- New registrations are first due after one configured interval.
- Pagination defaults to 20 and is capped at 100.
- JSON unknown fields are rejected.
- Custom report ranges are limited to 31 days.
- Reports cover all appliances; no appliance filter is included.
- Current and future UTC dates cannot be persisted as daily reports.
- Daily dates are the public identifiers instead of report UUIDs.
- Correlation IDs use the specified 64-character restricted format.
- Public metric values and aggregates are JSON decimals.
- Collection finalization uses the latest appliance state and interval.

## 14. Open decisions

These do not change the proposed endpoint topology:

- Exact canonical metric-name and unit catalogs.
- Decimal precision, scale, and average-rounding behavior.
- Final warning-code catalog for partial vendor responses.
- Historical metric and daily-report retention periods.
- Exact mock-vendor fault-control mechanism.
- Whether optional prior-day sample data should be supplied for immediate non-empty daily Swagger verification.
