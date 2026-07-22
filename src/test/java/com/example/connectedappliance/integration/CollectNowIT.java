package com.example.connectedappliance.integration;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import com.example.connectedappliance.appliance.application.ApplianceCollectionConfigurationService;
import com.example.connectedappliance.appliance.application.UpdateCollectionStateCommand;
import com.example.connectedappliance.appliance.application.port.out.ApplianceRepository;
import com.example.connectedappliance.appliance.domain.Appliance;
import com.example.connectedappliance.appliance.domain.CollectionState;
import com.example.connectedappliance.appliance.domain.LastCollectionStatus;
import com.example.connectedappliance.shared.observability.CorrelationIdConstants;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(Task10FixedClockConfiguration.class)
@Execution(ExecutionMode.SAME_THREAD)
class CollectNowIT extends PostgresIntegrationTestSupport {

    private static final Instant CREATED = Instant.parse("2026-07-21T10:00:00Z");
    private static final Instant FIRST_COLLECTION = Instant.parse("2026-07-21T10:01:00Z");
    private static final String CORRELATION_ID = "task19-integration";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ApplianceRepository applianceRepository;

    @Autowired
    private ApplianceCollectionConfigurationService configurationService;

    @Autowired
    private Task10FixedClockConfiguration.MutableUtcClock clock;

    @BeforeEach
    @AfterEach
    void cleanDatabaseAndResetClock() {
        jdbcTemplate.update("DELETE FROM metric_sample");
        jdbcTemplate.update("DELETE FROM collection_warning");
        jdbcTemplate.update("DELETE FROM collection_attempt");
        jdbcTemplate.update("DELETE FROM appliance");
        clock.set(FIRST_COLLECTION);
    }

    @Test
    void bodylessCollectNowReturnsAndPersistsTheManualMockAlphaAttempt() throws Exception {
        UUID applianceId = insertActiveAppliance("task19-success");

        ResponseEntity<String> response = collectNow(applianceId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(response.getHeaders().getFirst(CorrelationIdConstants.HEADER_NAME))
                .isEqualTo(CORRELATION_ID);
        JsonNode body = objectMapper.readTree(response.getBody());
        UUID attemptId = UUID.fromString(body.path("id").asText());
        assertThat(body.path("applianceId").asText()).isEqualTo(applianceId.toString());
        assertThat(body.path("trigger").asText()).isEqualTo("MANUAL");
        assertThat(body.path("outcome").asText()).isEqualTo("SUCCESS");
        assertThat(Instant.parse(body.path("completedAt").asText()))
                .isAfterOrEqualTo(Instant.parse(body.path("startedAt").asText()));
        assertThat(body.path("sampleCount").asInt()).isEqualTo(2);
        assertThat(body.path("warnings").isEmpty()).isTrue();
        assertThat(body.path("failure").isNull()).isTrue();
        Instant due = Instant.parse(body.path("nextCollectionDueAt").asText());

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM collection_attempt WHERE id = ?",
                        Integer.class,
                        attemptId))
                .isEqualTo(1);
        Map<String, Object> persistedAttempt = jdbcTemplate.queryForMap(
                """
                SELECT appliance_id, trigger, outcome, started_at, completed_at, sample_count,
                       failure_category, failure_message, retry_after_seconds,
                       next_collection_due_at
                FROM collection_attempt WHERE id = ?
                """,
                attemptId);
        assertThat(persistedAttempt)
                .containsEntry("appliance_id", applianceId)
                .containsEntry("trigger", "MANUAL")
                .containsEntry("outcome", "SUCCESS")
                .containsEntry("sample_count", 2)
                .containsEntry("next_collection_due_at", java.sql.Timestamp.from(due));
        assertThat(persistedAttempt.get("failure_category")).isNull();
        assertThat(persistedAttempt.get("failure_message")).isNull();
        assertThat(persistedAttempt.get("retry_after_seconds")).isNull();
        assertThat(persistedAttempt.get("started_at"))
                .isEqualTo(java.sql.Timestamp.from(Instant.parse(body.path("startedAt").asText())));
        assertThat(persistedAttempt.get("completed_at"))
                .isEqualTo(java.sql.Timestamp.from(Instant.parse(body.path("completedAt").asText())));
        assertThat(countByAttempt("collection_warning", attemptId)).isZero();
        assertThat(countByAttempt("metric_sample", attemptId)).isEqualTo(2);
        assertThat(jdbcTemplate.query(
                        """
                        SELECT appliance_id, metric_name, unit, value, observed_at = ingested_at
                        FROM metric_sample WHERE collection_attempt_id = ? ORDER BY metric_name
                        """,
                        (resultSet, rowNumber) -> tuple(
                                resultSet.getObject("appliance_id", UUID.class),
                                resultSet.getString("metric_name"),
                                resultSet.getString("unit"),
                                resultSet.getBigDecimal("value"),
                                resultSet.getBoolean(5)),
                        attemptId))
                .containsExactly(
                        tuple(
                                applianceId,
                                "POWER",
                                "WATT",
                                new BigDecimal("125.000000"),
                                true),
                        tuple(
                                applianceId,
                                "TEMPERATURE",
                                "CELSIUS",
                                new BigDecimal("21.500000"),
                                true));

        Appliance updated = applianceRepository.findById(applianceId).orElseThrow();
        assertThat(updated.consecutiveFailureCount()).isZero();
        assertThat(updated.lastCollectionStatus()).isEqualTo(LastCollectionStatus.SUCCESS);
        assertThat(updated.nextCollectionDueAt()).isEqualTo(due);
        assertThat(updated.version()).isEqualTo(1);
    }

    @Test
    void repeatedSequentialCollectNowCreatesDistinctAttemptsAndSamples() throws Exception {
        UUID applianceId = insertActiveAppliance("task19-repeat");

        ResponseEntity<String> firstResponse = collectNow(applianceId);
        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode first = objectMapper.readTree(firstResponse.getBody());
        clock.set(FIRST_COLLECTION.plusSeconds(10));
        ResponseEntity<String> secondResponse = collectNow(applianceId);
        assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode second = objectMapper.readTree(secondResponse.getBody());

        assertThat(first.path("id").asText()).isNotEqualTo(second.path("id").asText());
        assertThat(first.path("trigger").asText()).isEqualTo("MANUAL");
        assertThat(second.path("trigger").asText()).isEqualTo("MANUAL");
        assertThat(Instant.parse(second.path("nextCollectionDueAt").asText()))
                .isAfter(Instant.parse(first.path("nextCollectionDueAt").asText()));
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM collection_attempt WHERE appliance_id = ?",
                        Integer.class,
                        applianceId))
                .isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM metric_sample WHERE appliance_id = ?",
                        Integer.class,
                        applianceId))
                .isEqualTo(4);
        Appliance updated = applianceRepository.findById(applianceId).orElseThrow();
        assertThat(updated.nextCollectionDueAt())
                .isEqualTo(Instant.parse(second.path("nextCollectionDueAt").asText()));
        assertThat(updated.version()).isEqualTo(2);
    }

    @Test
    void pausedCollectNowReturnsConflictWithoutPersistentSideEffects() throws Exception {
        UUID applianceId = insertActiveAppliance("task19-paused");
        clock.set(FIRST_COLLECTION.minusSeconds(10));
        configurationService.updateCollectionState(
                new UpdateCollectionStateCommand(applianceId, CollectionState.PAUSED));
        Appliance pausedBeforeCall = applianceRepository.findById(applianceId).orElseThrow();

        ResponseEntity<String> response = collectNow(applianceId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.path("code").asText()).isEqualTo("APPLIANCE_PAUSED");
        assertThat(body.path("correlationId").asText()).isEqualTo(CORRELATION_ID);
        assertThat(countRows("collection_attempt")).isZero();
        assertThat(countRows("collection_warning")).isZero();
        assertThat(countRows("metric_sample")).isZero();

        Appliance after = applianceRepository.findById(applianceId).orElseThrow();
        assertThat(after.collectionState()).isEqualTo(CollectionState.PAUSED);
        assertThat(after.nextCollectionDueAt()).isNull();
        assertThat(after.consecutiveFailureCount())
                .isEqualTo(pausedBeforeCall.consecutiveFailureCount());
        assertThat(after.lastCollectionStatus()).isEqualTo(pausedBeforeCall.lastCollectionStatus());
        assertThat(after.updatedAt()).isEqualTo(pausedBeforeCall.updatedAt());
        assertThat(after.version()).isEqualTo(pausedBeforeCall.version());
    }

    private ResponseEntity<String> collectNow(UUID applianceId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set(CorrelationIdConstants.HEADER_NAME, CORRELATION_ID);
        return restTemplate.exchange(
                "/api/v1/appliances/{applianceId}/actions/collect-now",
                HttpMethod.POST,
                new HttpEntity<Void>(headers),
                String.class,
                applianceId);
    }

    private UUID insertActiveAppliance(String reference) {
        UUID applianceId = UUID.randomUUID();
        applianceRepository.insert(new Appliance(
                applianceId,
                "Task 19 appliance",
                "Collect-now integration test",
                "mock-alpha",
                reference,
                CollectionState.ACTIVE,
                30,
                CREATED.plusSeconds(30),
                0,
                LastCollectionStatus.NEVER_ATTEMPTED,
                0,
                CREATED,
                CREATED));
        return applianceId;
    }

    private int countRows(String table) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    private int countByAttempt(String table, UUID attemptId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM " + table + " WHERE collection_attempt_id = ?",
                Integer.class,
                attemptId);
    }
}
