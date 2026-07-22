package com.example.connectedappliance.integration;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.example.connectedappliance.appliance.application.ApplianceCollectionConfigurationService;
import com.example.connectedappliance.appliance.application.UpdateCollectionIntervalCommand;
import com.example.connectedappliance.appliance.application.UpdateCollectionStateCommand;
import com.example.connectedappliance.appliance.application.port.out.ApplianceRepository;
import com.example.connectedappliance.appliance.domain.Appliance;
import com.example.connectedappliance.appliance.domain.CollectionState;
import com.example.connectedappliance.appliance.domain.LastCollectionStatus;
import com.example.connectedappliance.shared.observability.CorrelationIdConstants;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(Task10FixedClockConfiguration.class)
@Execution(ExecutionMode.SAME_THREAD)
class ApplianceConcurrencyIT extends PostgresIntegrationTestSupport {

    private static final Instant CREATED_AT = Instant.parse("2026-07-21T10:00:00.123456Z");
    private static final Instant COMMAND_TIME = Instant.parse("2026-07-21T10:10:00.654321Z");
    private static final Instant LATER_TIME = Instant.parse("2026-07-21T10:20:00.111111Z");
    private static final String CORRELATION_ID = "task13-integration";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ApplianceRepository applianceRepository;

    @Autowired
    private ApplianceCollectionConfigurationService service;

    @Autowired
    private Task10FixedClockConfiguration.MutableUtcClock clock;

    @BeforeEach
    void prepare() {
        jdbcTemplate.update("DELETE FROM appliance");
        clock.set(COMMAND_TIME);
    }

    @AfterEach
    void clean() {
        jdbcTemplate.update("DELETE FROM appliance");
        clock.set(Task10FixedClockConfiguration.REGISTRATION_TIME);
    }

    @Test
    void activeIntervalChangeReschedulesOncePreservesStateAndSupportsReadAfterWrite()
            throws Exception {
        UUID id = id(1);
        applianceRepository.insert(appliance(id, CollectionState.ACTIVE, 30));
        Map<String, Object> before = row(id);

        putInterval(id, 60)
                .andExpect(jsonPath("$.collectionIntervalSeconds").value(60))
                .andExpect(jsonPath("$.collectionState").value("ACTIVE"))
                .andExpect(jsonPath("$.nextCollectionDueAt")
                        .value("2026-07-21T10:11:00.654321Z"))
                .andExpect(jsonPath("$.updatedAt").value("2026-07-21T10:10:00.654321Z"));

        Map<String, Object> changed = row(id);
        assertThat(changed.get("collection_interval_seconds")).isEqualTo(60);
        assertThat(databaseInstant(changed.get("next_collection_due_at")))
                .isEqualTo(COMMAND_TIME.plusSeconds(60));
        assertThat(databaseInstant(changed.get("updated_at"))).isEqualTo(COMMAND_TIME);
        assertThat(changed.get("version")).isEqualTo(1L);
        assertUnchangedOutsideCollectionConfiguration(before, changed);
        assertDueInvariant(changed);

        mockMvc.perform(get("/api/v1/appliances/{applianceId}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.collectionIntervalSeconds").value(60))
                .andExpect(jsonPath("$.nextCollectionDueAt")
                        .value("2026-07-21T10:11:00.654321Z"))
                .andExpect(jsonPath("$.version").doesNotExist());
        mockMvc.perform(get("/api/v1/appliances"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(id.toString()))
                .andExpect(jsonPath("$.items[0].collectionIntervalSeconds").value(60))
                .andExpect(jsonPath("$.items[0].version").doesNotExist());
    }

    @Test
    void activeIntervalNoOpPreservesEveryDatabaseFieldAtALaterClockTime() throws Exception {
        UUID id = id(2);
        applianceRepository.insert(appliance(id, CollectionState.ACTIVE, 30));
        Map<String, Object> before = row(id);
        clock.set(LATER_TIME);

        putInterval(id, 30)
                .andExpect(jsonPath("$.nextCollectionDueAt")
                        .value("2026-07-21T10:00:30.123456Z"))
                .andExpect(jsonPath("$.updatedAt").value("2026-07-21T10:00:00.123456Z"));

        Map<String, Object> after = row(id);
        assertThat(after).isEqualTo(before);
        assertThat(after.get("version")).isEqualTo(0L);
        assertDueInvariant(after);
    }

    @Test
    void pausedIntervalChangeKeepsDueNullAndIncrementsVersionOnce() throws Exception {
        UUID id = id(3);
        applianceRepository.insert(appliance(id, CollectionState.PAUSED, 30));
        Map<String, Object> before = row(id);

        putInterval(id, 60)
                .andExpect(jsonPath("$.collectionIntervalSeconds").value(60))
                .andExpect(jsonPath("$.collectionState").value("PAUSED"))
                .andExpect(jsonPath("$.nextCollectionDueAt")
                        .value(org.hamcrest.Matchers.nullValue()));

        Map<String, Object> changed = row(id);
        assertThat(changed.get("collection_interval_seconds")).isEqualTo(60);
        assertThat(changed.get("collection_state")).isEqualTo("PAUSED");
        assertThat(changed.get("next_collection_due_at")).isNull();
        assertThat(databaseInstant(changed.get("updated_at"))).isEqualTo(COMMAND_TIME);
        assertThat(changed.get("version")).isEqualTo(1L);
        assertUnchangedOutsideCollectionConfiguration(before, changed);
        assertDueInvariant(changed);
    }

    @Test
    void pauseClearsDueTimeAndPreservesIntervalAndAllUnrelatedFields() throws Exception {
        UUID id = id(4);
        applianceRepository.insert(appliance(id, CollectionState.ACTIVE, 45));
        Map<String, Object> before = row(id);

        putState(id, "PAUSED")
                .andExpect(jsonPath("$.collectionState").value("PAUSED"))
                .andExpect(jsonPath("$.collectionIntervalSeconds").value(45))
                .andExpect(jsonPath("$.nextCollectionDueAt")
                        .value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.updatedAt").value("2026-07-21T10:10:00.654321Z"));

        Map<String, Object> changed = row(id);
        assertThat(changed.get("collection_state")).isEqualTo("PAUSED");
        assertThat(changed.get("next_collection_due_at")).isNull();
        assertThat(changed.get("collection_interval_seconds")).isEqualTo(45);
        assertThat(changed.get("version")).isEqualTo(1L);
        assertUnchangedOutsideCollectionConfiguration(before, changed);
        assertDueInvariant(changed);
    }

    @Test
    void resumeIsDueExactlyAtCommandTimeWithoutAddingInterval() throws Exception {
        UUID id = id(5);
        applianceRepository.insert(appliance(id, CollectionState.PAUSED, 45));
        Map<String, Object> before = row(id);

        putState(id, "ACTIVE")
                .andExpect(jsonPath("$.collectionState").value("ACTIVE"))
                .andExpect(jsonPath("$.collectionIntervalSeconds").value(45))
                .andExpect(jsonPath("$.nextCollectionDueAt")
                        .value("2026-07-21T10:10:00.654321Z"));

        Map<String, Object> changed = row(id);
        assertThat(changed.get("collection_state")).isEqualTo("ACTIVE");
        assertThat(databaseInstant(changed.get("next_collection_due_at")))
                .isEqualTo(COMMAND_TIME)
                .isNotEqualTo(COMMAND_TIME.plusSeconds(45));
        assertThat(databaseInstant(changed.get("updated_at"))).isEqualTo(COMMAND_TIME);
        assertThat(changed.get("version")).isEqualTo(1L);
        assertUnchangedOutsideCollectionConfiguration(before, changed);
        assertDueInvariant(changed);
    }

    @Test
    void repeatedActiveAndPausedStatesAreExactNoOpsAtALaterClockTime() throws Exception {
        UUID activeId = id(6);
        UUID pausedId = id(7);
        applianceRepository.insert(appliance(activeId, CollectionState.ACTIVE, 30));
        applianceRepository.insert(appliance(pausedId, CollectionState.PAUSED, 30));
        Map<String, Object> activeBefore = row(activeId);
        Map<String, Object> pausedBefore = row(pausedId);
        clock.set(LATER_TIME);

        putState(activeId, "ACTIVE")
                .andExpect(jsonPath("$.nextCollectionDueAt")
                        .value("2026-07-21T10:00:30.123456Z"));
        putState(pausedId, "PAUSED")
                .andExpect(jsonPath("$.nextCollectionDueAt")
                        .value(org.hamcrest.Matchers.nullValue()));

        assertThat(row(activeId)).isEqualTo(activeBefore);
        assertThat(row(pausedId)).isEqualTo(pausedBefore);
        assertDueInvariant(row(activeId));
        assertDueInvariant(row(pausedId));
    }

    @Test
    void bothOperationsReturnFeatureNotFoundAndCreateNoRows() throws Exception {
        UUID missingIntervalId = id(98);
        UUID missingStateId = id(99);

        mockMvc.perform(put(
                                "/api/v1/appliances/{applianceId}/collection-interval",
                                missingIntervalId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"collectionIntervalSeconds\":60}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("APPLIANCE_NOT_FOUND"));
        mockMvc.perform(put(
                                "/api/v1/appliances/{applianceId}/collection-state",
                                missingStateId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"collectionState\":\"PAUSED\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("APPLIANCE_NOT_FOUND"));

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM appliance", Long.class))
                .isZero();
    }

    @Test
    @Timeout(20)
    void concurrentIntervalAndPauseSerializeWithoutLosingEitherChange() throws Exception {
        UUID id = id(8);
        applianceRepository.insert(appliance(id, CollectionState.ACTIVE, 30));
        Map<String, Object> before = row(id);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Appliance> intervalResult = executor.submit(() -> {
                awaitStart(ready, start);
                return service.updateCollectionInterval(
                        new UpdateCollectionIntervalCommand(id, 60));
            });
            Future<Appliance> pauseResult = executor.submit(() -> {
                awaitStart(ready, start);
                return service.updateCollectionState(
                        new UpdateCollectionStateCommand(id, CollectionState.PAUSED));
            });

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(intervalResult.get(10, TimeUnit.SECONDS).collectionIntervalSeconds())
                    .isEqualTo(60);
            assertThat(pauseResult.get(10, TimeUnit.SECONDS).collectionState())
                    .isEqualTo(CollectionState.PAUSED);
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }

        Map<String, Object> finalRow = row(id);
        assertThat(finalRow.get("collection_interval_seconds")).isEqualTo(60);
        assertThat(finalRow.get("collection_state")).isEqualTo("PAUSED");
        assertThat(finalRow.get("next_collection_due_at")).isNull();
        assertThat(finalRow.get("version")).isEqualTo(2L);
        assertUnchangedOutsideCollectionConfiguration(before, finalRow);
        assertDueInvariant(finalRow);
    }

    private void awaitStart(CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent mutation start gate timed out");
        }
    }

    private org.springframework.test.web.servlet.ResultActions putInterval(UUID id, int interval)
            throws Exception {
        return mockMvc.perform(put(
                                "/api/v1/appliances/{applianceId}/collection-interval", id)
                        .header(CorrelationIdConstants.HEADER_NAME, CORRELATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"collectionIntervalSeconds\":%d}".formatted(interval)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(header().string(CorrelationIdConstants.HEADER_NAME, CORRELATION_ID))
                .andExpect(jsonPath("$.version").doesNotExist());
    }

    private org.springframework.test.web.servlet.ResultActions putState(UUID id, String state)
            throws Exception {
        return mockMvc.perform(put(
                                "/api/v1/appliances/{applianceId}/collection-state", id)
                        .header(CorrelationIdConstants.HEADER_NAME, CORRELATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"collectionState\":\"%s\"}".formatted(state)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(header().string(CorrelationIdConstants.HEADER_NAME, CORRELATION_ID))
                .andExpect(jsonPath("$.version").doesNotExist());
    }

    private Appliance appliance(UUID id, CollectionState state, int interval) {
        return new Appliance(
                id,
                "Task 13 appliance " + id,
                "Preserved description",
                "mock-alpha",
                "task13-ref-" + id,
                state,
                interval,
                state == CollectionState.ACTIVE ? CREATED_AT.plusSeconds(30) : null,
                3,
                LastCollectionStatus.FAILED,
                0,
                CREATED_AT,
                CREATED_AT);
    }

    private Map<String, Object> row(UUID id) {
        return jdbcTemplate.queryForMap(
                """
                SELECT id, display_name, description, vendor_key, external_reference,
                       collection_state, collection_interval_seconds, next_collection_due_at,
                       consecutive_failure_count, last_collection_status, version,
                       created_at, updated_at
                FROM appliance
                WHERE id = ?
                """,
                id);
    }

    private void assertUnchangedOutsideCollectionConfiguration(
            Map<String, Object> before, Map<String, Object> after) {
        for (String column : new String[] {
                "id",
                "display_name",
                "description",
                "vendor_key",
                "external_reference",
                "consecutive_failure_count",
                "last_collection_status",
                "created_at"
        }) {
            assertThat(after.get(column)).as(column).isEqualTo(before.get(column));
        }
    }

    private void assertDueInvariant(Map<String, Object> applianceRow) {
        if ("ACTIVE".equals(applianceRow.get("collection_state"))) {
            assertThat(applianceRow.get("next_collection_due_at")).isNotNull();
        } else {
            assertThat(applianceRow.get("collection_state")).isEqualTo("PAUSED");
            assertThat(applianceRow.get("next_collection_due_at")).isNull();
        }
    }

    private Instant databaseInstant(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        return ((Timestamp) value).toInstant();
    }

    private UUID id(int suffix) {
        return UUID.fromString("00000000-0000-0000-0000-%012d".formatted(suffix));
    }
}
