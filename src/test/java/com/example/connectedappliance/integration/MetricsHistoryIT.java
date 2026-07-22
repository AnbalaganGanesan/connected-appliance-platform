package com.example.connectedappliance.integration;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.connectedappliance.appliance.application.port.out.ApplianceRepository;
import com.example.connectedappliance.appliance.domain.Appliance;
import com.example.connectedappliance.appliance.domain.CollectionState;
import com.example.connectedappliance.appliance.domain.LastCollectionStatus;
import com.example.connectedappliance.metrics.application.port.out.MetricsRepository;
import com.example.connectedappliance.metrics.domain.CollectionAttempt;
import com.example.connectedappliance.metrics.domain.CollectionFailure;
import com.example.connectedappliance.metrics.domain.CollectionFailureCategory;
import com.example.connectedappliance.metrics.domain.CollectionOutcome;
import com.example.connectedappliance.metrics.domain.CollectionTrigger;
import com.example.connectedappliance.metrics.domain.CollectionWarning;
import com.example.connectedappliance.metrics.domain.CompletedCollection;
import com.example.connectedappliance.metrics.domain.MetricSample;
import com.example.connectedappliance.shared.metric.CanonicalMetric;
import com.example.connectedappliance.shared.metric.CanonicalUnit;
import com.example.connectedappliance.shared.observability.CorrelationIdConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(Task10FixedClockConfiguration.class)
@Execution(ExecutionMode.SAME_THREAD)
class MetricsHistoryIT extends PostgresIntegrationTestSupport {

    private static final Instant BASE = Instant.parse("2026-07-21T10:00:00Z");
    private static final String CORRELATION_ID = "task20-integration";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ApplianceRepository applianceRepository;

    @Autowired
    private MetricsRepository metricsRepository;

    @Autowired
    private Task10FixedClockConfiguration.MutableUtcClock clock;

    @BeforeEach
    @AfterEach
    void cleanDatabaseAndResetClock() {
        jdbcTemplate.update("DELETE FROM metric_sample");
        jdbcTemplate.update("DELETE FROM collection_warning");
        jdbcTemplate.update("DELETE FROM collection_attempt");
        jdbcTemplate.update("DELETE FROM appliance");
        clock.set(BASE.plusSeconds(60));
    }

    @Test
    void reviewerCollectNowOutputIsRetrievableThroughBothHistoryOperations()
            throws Exception {
        UUID applianceId = insertAppliance("workflow", CollectionState.ACTIVE);

        ResponseEntity<String> collectResponse = request(
                HttpMethod.POST,
                URI.create("/api/v1/appliances/" + applianceId + "/actions/collect-now"));
        assertThat(collectResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode collected = objectMapper.readTree(collectResponse.getBody());
        UUID attemptId = UUID.fromString(collected.path("id").asText());
        String dueSnapshot = collected.path("nextCollectionDueAt").asText();
        Instant observedAt = Instant.parse(collected.path("completedAt").asText());
        int attemptCount = rowCount("collection_attempt");
        int sampleCount = rowCount("metric_sample");

        jdbcTemplate.update(
                "UPDATE appliance SET next_collection_due_at = ?, updated_at = ?, version = version + 1 WHERE id = ?",
                jdbcTime(observedAt.plusSeconds(300)),
                jdbcTime(observedAt.plusSeconds(2)),
                applianceId);

        ResponseEntity<String> attemptsResponse = request(
                HttpMethod.GET,
                uri("/api/v1/appliances/" + applianceId + "/collection-attempts")
                        .queryParam("trigger", "MANUAL")
                        .queryParam("page", 0)
                        .queryParam("size", 20)
                        .build().encode().toUri());
        assertThat(attemptsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode attempts = objectMapper.readTree(attemptsResponse.getBody());
        assertThat(attempts.path("items")).hasSize(1);
        assertThat(attempts.path("items").get(0).path("id").asText())
                .isEqualTo(attemptId.toString());
        assertThat(attempts.path("items").get(0).path("trigger").asText()).isEqualTo("MANUAL");
        assertThat(attempts.path("items").get(0).path("outcome").asText()).isEqualTo("SUCCESS");
        assertThat(attempts.path("items").get(0).path("sampleCount").asInt()).isEqualTo(2);
        assertThat(attempts.path("items").get(0).path("warnings")).isEmpty();
        assertThat(attempts.path("items").get(0).path("failure").isNull()).isTrue();
        assertThat(attempts.path("items").get(0).path("nextCollectionDueAt").asText())
                .isEqualTo(dueSnapshot);

        ResponseEntity<String> metricsResponse = request(
                HttpMethod.GET,
                metricUri(applianceId, observedAt.minusSeconds(1), observedAt.plusSeconds(1), 0, 100));
        assertThat(metricsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode metrics = objectMapper.readTree(metricsResponse.getBody());
        assertThat(metrics.path("items")).hasSize(2);
        assertThat(metrics.path("items").findValuesAsText("collectionAttemptId"))
                .containsExactly(attemptId.toString(), attemptId.toString());
        assertThat(metrics.path("items").findValuesAsText("metricName"))
                .containsExactlyInAnyOrder("TEMPERATURE", "POWER");
        assertMetric(metrics.path("items"), "TEMPERATURE", "CELSIUS", "21.500000");
        assertMetric(metrics.path("items"), "POWER", "WATT", "125.000000");
        assertThat(metrics.path("items").findValuesAsText("applianceId"))
                .containsOnly(applianceId.toString());

        assertThat(rowCount("collection_attempt")).isEqualTo(attemptCount);
        assertThat(rowCount("metric_sample")).isEqualTo(sampleCount);
    }

    @Test
    void attemptHistoryAppliesFixedOrderingAllFiltersPaginationWarningsAndSnapshots()
            throws Exception {
        UUID applianceId = insertAppliance("attempts", CollectionState.ACTIVE);
        UUID otherAppliance = insertAppliance("attempts-other", CollectionState.ACTIVE);
        UUID lower = uuid(1);
        UUID higher = uuid(2);
        UUID partial = uuid(3);
        UUID failed = uuid(4);
        insertSuccess(applianceId, higher, BASE.plusSeconds(30), CollectionTrigger.SCHEDULED, "1");
        insertSuccess(applianceId, lower, BASE.plusSeconds(30), CollectionTrigger.MANUAL, "2");
        insertPartial(applianceId, partial, BASE.plusSeconds(20));
        insertFailed(applianceId, failed, BASE.plusSeconds(10));
        insertSuccess(otherAppliance, uuid(5), BASE.plusSeconds(40), CollectionTrigger.MANUAL, "3");

        JsonNode page0 = body(request(HttpMethod.GET, attemptUri(applianceId, null, null, 0, 2)));
        JsonNode page1 = body(request(HttpMethod.GET, attemptUri(applianceId, null, null, 1, 2)));
        JsonNode beyond = body(request(HttpMethod.GET, attemptUri(applianceId, null, null, 2, 2)));
        assertThat(ids(page0)).containsExactly(lower.toString(), higher.toString());
        assertThat(ids(page1)).containsExactly(partial.toString(), failed.toString());
        assertThat(page0.path("totalElements").asLong()).isEqualTo(4);
        assertThat(page0.path("totalPages").asInt()).isEqualTo(2);
        assertThat(beyond.path("items")).isEmpty();
        assertThat(beyond.path("page").asInt()).isEqualTo(2);
        assertThat(beyond.path("totalElements").asLong()).isEqualTo(4);

        assertIds(applianceId, "MANUAL", null, lower, partial);
        assertIds(applianceId, "SCHEDULED", null, higher, failed);
        assertIds(applianceId, null, "SUCCESS", lower, higher);
        assertIds(applianceId, null, "PARTIAL_SUCCESS", partial);
        assertIds(applianceId, null, "FAILED", failed);
        assertIds(applianceId, "MANUAL", "PARTIAL_SUCCESS", partial);
        assertIds(applianceId, "SCHEDULED", "PARTIAL_SUCCESS");

        JsonNode partialItem = body(request(
                        HttpMethod.GET,
                        attemptUri(applianceId, "MANUAL", "PARTIAL_SUCCESS", 0, 20)))
                .path("items").get(0);
        assertThat(partialItem.path("warnings").findValuesAsText("code"))
                .containsExactly("UNKNOWN_METRIC", "MALFORMED_VALUE");
        assertThat(partialItem.path("nextCollectionDueAt").asText())
                .isEqualTo(BASE.plusSeconds(90).toString());
        JsonNode failedItem = body(request(
                        HttpMethod.GET,
                        attemptUri(applianceId, null, "FAILED", 0, 20)))
                .path("items").get(0);
        assertThat(failedItem.path("nextCollectionDueAt").isNull()).isTrue();
        assertThat(failedItem.path("failure").path("category").asText()).isEqualTo("TIMEOUT");
    }

    @Test
    void metricHistoryUsesObservedAtInclusiveExclusiveOrderPaginationAndNoDeduplication()
            throws Exception {
        UUID applianceId = insertAppliance("metrics", CollectionState.ACTIVE);
        UUID otherAppliance = insertAppliance("metrics-other", CollectionState.ACTIVE);
        Instant from = BASE.plusSeconds(100);
        Instant to = from.plusSeconds(10);
        UUID attemptId = uuid(20);
        metricsRepository.insert(success(
                applianceId,
                attemptId,
                BASE,
                List.of(
                        sample(uuid(100), applianceId, attemptId, "1", from.minus(1, ChronoUnit.MICROS), to),
                        sample(uuid(102), applianceId, attemptId, "21.500000", from, to.plusSeconds(20)),
                        sample(uuid(101), applianceId, attemptId, "21.500000", from, from.minusSeconds(20)),
                        sample(uuid(103), applianceId, attemptId, "3", from.plusSeconds(5), from),
                        sample(uuid(104), applianceId, attemptId, "4", to.minus(1, ChronoUnit.MICROS), from),
                        sample(uuid(105), applianceId, attemptId, "5", to, from),
                        sample(uuid(106), applianceId, attemptId, "6", to.plus(1, ChronoUnit.MICROS), from))));
        UUID otherAttempt = uuid(21);
        metricsRepository.insert(success(
                otherAppliance,
                otherAttempt,
                BASE,
                List.of(sample(uuid(107), otherAppliance, otherAttempt, "7", from, from))));

        JsonNode first = body(request(HttpMethod.GET, metricUri(applianceId, from, to, 0, 2)));
        JsonNode second = body(request(HttpMethod.GET, metricUri(applianceId, from, to, 1, 2)));
        JsonNode beyond = body(request(HttpMethod.GET, metricUri(applianceId, from, to, 2, 2)));

        assertThat(ids(first)).containsExactly(uuid(101).toString(), uuid(102).toString());
        assertThat(ids(second)).containsExactly(uuid(103).toString(), uuid(104).toString());
        assertThat(first.path("items").get(0).path("value").isNumber()).isTrue();
        assertThat(first.path("items").get(0).path("value").decimalValue())
                .isEqualByComparingTo(new BigDecimal("21.500000"));
        assertThat(first.path("items").get(0).path("ingestedAt").asText())
                .isEqualTo(from.minusSeconds(20).toString());
        assertThat(first.path("totalElements").asLong()).isEqualTo(4);
        assertThat(first.path("totalPages").asInt()).isEqualTo(2);
        assertThat(beyond.path("items")).isEmpty();
        assertThat(ids(first)).doesNotContain(uuid(100).toString(), uuid(105).toString(),
                uuid(106).toString(), uuid(107).toString());
    }

    @Test
    void existingPausedApplianceGetsEmptyPagesWhileMissingApplianceGetsNotFound()
            throws Exception {
        UUID paused = insertAppliance("empty-paused", CollectionState.PAUSED);
        UUID missing = UUID.randomUUID();

        ResponseEntity<String> emptyAttempts = request(
                HttpMethod.GET, attemptUri(paused, null, null, 3, 20));
        ResponseEntity<String> emptyMetrics = request(
                HttpMethod.GET, metricUri(paused, BASE, BASE.plusSeconds(1), 4, 20));
        assertEmptyPage(emptyAttempts, 3, 20);
        assertEmptyPage(emptyMetrics, 4, 20);

        ResponseEntity<String> missingAttempts = request(
                HttpMethod.GET, attemptUri(missing, null, null, 0, 20));
        ResponseEntity<String> missingMetrics = request(
                HttpMethod.GET, metricUri(missing, BASE, BASE.plusSeconds(1), 0, 20));
        assertThat(missingAttempts.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(body(missingAttempts).path("code").asText()).isEqualTo("APPLIANCE_NOT_FOUND");
        assertThat(missingMetrics.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(body(missingMetrics).path("code").asText()).isEqualTo("APPLIANCE_NOT_FOUND");
        assertThat(rowCount("collection_attempt")).isZero();
        assertThat(rowCount("metric_sample")).isZero();
    }

    @Test
    void metricRangeErrorsUseApprovedMappingsWithoutSideEffects() throws Exception {
        UUID applianceId = insertAppliance("range-errors", CollectionState.ACTIVE);
        Appliance before = applianceRepository.findById(applianceId).orElseThrow();

        ResponseEntity<String> equal = request(
                HttpMethod.GET, metricUri(applianceId, BASE, BASE, 0, 20));
        ResponseEntity<String> nonUtc = request(
                HttpMethod.GET,
                uri("/api/v1/appliances/" + applianceId + "/metrics")
                        .queryParam("from", "2026-07-21T10:00:00+00:00")
                        .queryParam("to", BASE.plusSeconds(1))
                        .build().encode().toUri());
        ResponseEntity<String> missing = request(
                HttpMethod.GET,
                uri("/api/v1/appliances/" + applianceId + "/metrics")
                        .queryParam("from", BASE)
                        .build().encode().toUri());

        assertThat(equal.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(body(equal).path("code").asText()).isEqualTo("INVALID_TIME_RANGE");
        assertThat(nonUtc.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(body(nonUtc).path("code").asText()).isEqualTo("VALIDATION_ERROR");
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(body(missing).path("code").asText()).isEqualTo("VALIDATION_ERROR");
        assertThat(applianceRepository.findById(applianceId).orElseThrow())
                .usingRecursiveComparison()
                .isEqualTo(before);
        assertThat(rowCount("collection_attempt")).isZero();
        assertThat(rowCount("collection_warning")).isZero();
        assertThat(rowCount("metric_sample")).isZero();
    }

    private void assertIds(
            UUID applianceId, String trigger, String outcome, UUID... expected) throws Exception {
        JsonNode response = body(request(
                HttpMethod.GET, attemptUri(applianceId, trigger, outcome, 0, 20)));
        assertThat(ids(response)).containsExactly(
                java.util.Arrays.stream(expected).map(UUID::toString).toArray(String[]::new));
        assertThat(response.path("totalElements").asLong()).isEqualTo(expected.length);
    }

    private void assertEmptyPage(ResponseEntity<String> response, int page, int size)
            throws Exception {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = body(response);
        assertThat(body.path("items")).isEmpty();
        assertThat(body.path("page").asInt()).isEqualTo(page);
        assertThat(body.path("size").asInt()).isEqualTo(size);
        assertThat(body.path("totalElements").asLong()).isZero();
        assertThat(body.path("totalPages").asInt()).isZero();
    }

    private void assertMetric(JsonNode items, String metric, String unit, String value) {
        JsonNode item = java.util.stream.StreamSupport.stream(items.spliterator(), false)
                .filter(candidate -> metric.equals(candidate.path("metricName").asText()))
                .findFirst()
                .orElseThrow();
        assertThat(item.path("unit").asText()).isEqualTo(unit);
        assertThat(item.path("value").decimalValue()).isEqualByComparingTo(new BigDecimal(value));
    }

    private List<String> ids(JsonNode page) {
        return page.path("items").findValuesAsText("id");
    }

    private JsonNode body(ResponseEntity<String> response) throws Exception {
        return objectMapper.readTree(response.getBody());
    }

    private ResponseEntity<String> request(HttpMethod method, URI uri) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set(CorrelationIdConstants.HEADER_NAME, CORRELATION_ID);
        ResponseEntity<String> response = restTemplate.exchange(
                uri.toString(), method, new HttpEntity<Void>(headers), String.class);
        assertThat(response.getHeaders().getFirst(CorrelationIdConstants.HEADER_NAME))
                .isEqualTo(CORRELATION_ID);
        return response;
    }

    private URI attemptUri(
            UUID applianceId, String trigger, String outcome, int page, int size) {
        UriComponentsBuilder builder = uri(
                        "/api/v1/appliances/" + applianceId + "/collection-attempts")
                .queryParam("page", page)
                .queryParam("size", size);
        if (trigger != null) {
            builder.queryParam("trigger", trigger);
        }
        if (outcome != null) {
            builder.queryParam("outcome", outcome);
        }
        return builder.build().encode().toUri();
    }

    private URI metricUri(UUID applianceId, Instant from, Instant to, int page, int size) {
        return uri("/api/v1/appliances/" + applianceId + "/metrics")
                .queryParam("from", from)
                .queryParam("to", to)
                .queryParam("page", page)
                .queryParam("size", size)
                .build().encode().toUri();
    }

    private UriComponentsBuilder uri(String path) {
        return UriComponentsBuilder.fromPath(path);
    }

    private UUID insertAppliance(String reference, CollectionState state) {
        UUID id = UUID.randomUUID();
        applianceRepository.insert(new Appliance(
                id,
                "Task 20 appliance",
                "History integration test",
                "mock-alpha",
                reference + "-" + id,
                state,
                30,
                state == CollectionState.ACTIVE ? BASE.plusSeconds(30) : null,
                0,
                LastCollectionStatus.NEVER_ATTEMPTED,
                0,
                BASE,
                BASE));
        return id;
    }

    private void insertSuccess(
            UUID applianceId,
            UUID attemptId,
            Instant startedAt,
            CollectionTrigger trigger,
            String value) {
        MetricSample sample = sample(
                UUID.randomUUID(), applianceId, attemptId, value, startedAt, startedAt);
        metricsRepository.insert(new CompletedCollection(
                new CollectionAttempt(
                        attemptId,
                        applianceId,
                        trigger,
                        CollectionOutcome.SUCCESS,
                        startedAt,
                        startedAt.plusSeconds(1),
                        1,
                        List.of(),
                        null,
                        startedAt.plusSeconds(60)),
                List.of(sample)));
    }

    private void insertPartial(UUID applianceId, UUID attemptId, Instant startedAt) {
        MetricSample sample = sample(
                UUID.randomUUID(), applianceId, attemptId, "3", startedAt, startedAt);
        metricsRepository.insert(new CompletedCollection(
                new CollectionAttempt(
                        attemptId,
                        applianceId,
                        CollectionTrigger.MANUAL,
                        CollectionOutcome.PARTIAL_SUCCESS,
                        startedAt,
                        startedAt.plusSeconds(1),
                        1,
                        List.of(
                                new CollectionWarning("UNKNOWN_METRIC", "Unknown metric."),
                                new CollectionWarning("MALFORMED_VALUE", "Malformed value.")),
                        null,
                        BASE.plusSeconds(90)),
                List.of(sample)));
    }

    private void insertFailed(UUID applianceId, UUID attemptId, Instant startedAt) {
        metricsRepository.insert(new CompletedCollection(
                new CollectionAttempt(
                        attemptId,
                        applianceId,
                        CollectionTrigger.SCHEDULED,
                        CollectionOutcome.FAILED,
                        startedAt,
                        startedAt.plusSeconds(1),
                        0,
                        List.of(),
                        new CollectionFailure(CollectionFailureCategory.TIMEOUT, null, null),
                        null),
                List.of()));
    }

    private CompletedCollection success(
            UUID applianceId,
            UUID attemptId,
            Instant startedAt,
            List<MetricSample> samples) {
        return new CompletedCollection(
                new CollectionAttempt(
                        attemptId,
                        applianceId,
                        CollectionTrigger.MANUAL,
                        CollectionOutcome.SUCCESS,
                        startedAt,
                        startedAt.plusSeconds(1),
                        samples.size(),
                        List.of(),
                        null,
                        null),
                samples);
    }

    private MetricSample sample(
            UUID id,
            UUID applianceId,
            UUID attemptId,
            String value,
            Instant observedAt,
            Instant ingestedAt) {
        return new MetricSample(
                id,
                applianceId,
                attemptId,
                CanonicalMetric.TEMPERATURE,
                CanonicalUnit.CELSIUS,
                new BigDecimal(value),
                observedAt,
                ingestedAt);
    }

    private int rowCount(String table) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString(String.format("00000000-0000-0000-0000-%012d", suffix));
    }

    private static OffsetDateTime jdbcTime(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
