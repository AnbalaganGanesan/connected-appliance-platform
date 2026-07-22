package com.example.connectedappliance.integration;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

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
class ApplianceMetadataIT extends PostgresIntegrationTestSupport {

    private static final Instant CREATED_AT = Instant.parse("2026-07-21T10:00:00.123456Z");
    private static final Instant UPDATE_TIME = Instant.parse("2026-07-21T10:10:00.654321Z");
    private static final Instant LATER_TIME = Instant.parse("2026-07-21T10:20:00.111111Z");
    private static final String CORRELATION_ID = "task12-integration";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ApplianceRepository applianceRepository;

    @Autowired
    private Task10FixedClockConfiguration.MutableUtcClock clock;

    @BeforeEach
    void prepare() {
        jdbcTemplate.update("DELETE FROM appliance");
        clock.set(UPDATE_TIME);
    }

    @AfterEach
    void clean() {
        jdbcTemplate.update("DELETE FROM appliance");
    }

    @Test
    void replacesActiveMetadataOncePreservesOperationalStateAndSupportsReadAfterWrite()
            throws Exception {
        UUID id = id(1);
        applianceRepository.insert(appliance(
                id, "Original", "Original description", CollectionState.ACTIVE));
        Map<String, Object> before = row(id);

        mockMvc.perform(put("/api/v1/appliances/{applianceId}/metadata", id)
                        .header(CorrelationIdConstants.HEADER_NAME, CORRELATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"  Kitchen  ",
                                 "description":"  Ground floor  "}
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(header().string(CorrelationIdConstants.HEADER_NAME, CORRELATION_ID))
                .andExpect(jsonPath("$.displayName").value("Kitchen"))
                .andExpect(jsonPath("$.description").value("Ground floor"))
                .andExpect(jsonPath("$.collectionState").value("ACTIVE"))
                .andExpect(jsonPath("$.nextCollectionDueAt")
                        .value("2026-07-21T10:00:30.123456Z"))
                .andExpect(jsonPath("$.updatedAt").value("2026-07-21T10:10:00.654321Z"))
                .andExpect(jsonPath("$.version").doesNotExist());

        Map<String, Object> changed = row(id);
        assertThat(changed.get("display_name")).isEqualTo("Kitchen");
        assertThat(changed.get("description")).isEqualTo("Ground floor");
        assertThat(databaseInstant(changed.get("updated_at"))).isEqualTo(UPDATE_TIME);
        assertThat(changed.get("version")).isEqualTo(1L);
        assertNonMetadataUnchanged(before, changed);

        mockMvc.perform(get("/api/v1/appliances/{applianceId}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Kitchen"))
                .andExpect(jsonPath("$.description").value("Ground floor"))
                .andExpect(jsonPath("$.version").doesNotExist());
        mockMvc.perform(get("/api/v1/appliances"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(id.toString()))
                .andExpect(jsonPath("$.items[0].displayName").value("Kitchen"))
                .andExpect(jsonPath("$.items[0].description").value("Ground floor"))
                .andExpect(jsonPath("$.items[0].version").doesNotExist());
    }

    @Test
    void exactAndNormalizedIdenticalReplacementsIgnoreLaterClockAndRemainNoOps()
            throws Exception {
        UUID id = id(2);
        applianceRepository.insert(appliance(
                id, "Kitchen", "Ground floor", CollectionState.ACTIVE));

        putMetadata(id, "{\"displayName\":\"Kitchen\",\"description\":\"Ground floor\"}")
                .andExpect(jsonPath("$.updatedAt").value("2026-07-21T10:00:00.123456Z"));
        assertVersionAndTime(id, 0L, CREATED_AT);

        clock.set(LATER_TIME);
        putMetadata(
                        id,
                        "{\"displayName\":\"  Kitchen  \","
                                + "\"description\":\"  Ground floor  \"}")
                .andExpect(jsonPath("$.updatedAt").value("2026-07-21T10:00:00.123456Z"));
        assertVersionAndTime(id, 0L, CREATED_AT);
    }

    @Test
    void explicitNullClearsDescriptionOnceAndRepeatedClearIsANoOp() throws Exception {
        UUID id = id(3);
        applianceRepository.insert(appliance(
                id, "Kitchen", "Description", CollectionState.ACTIVE));
        Map<String, Object> before = row(id);

        putMetadata(id, "{\"displayName\":\"Kitchen\",\"description\":null}")
                .andExpect(jsonPath("$.description").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.updatedAt").value("2026-07-21T10:10:00.654321Z"));
        Map<String, Object> cleared = row(id);
        assertThat(cleared.get("description")).isNull();
        assertThat(cleared.get("version")).isEqualTo(1L);
        assertNonMetadataUnchanged(before, cleared);

        clock.set(LATER_TIME);
        putMetadata(id, "{\"displayName\":\"Kitchen\",\"description\":null}")
                .andExpect(jsonPath("$.updatedAt").value("2026-07-21T10:10:00.654321Z"));
        assertVersionAndTime(id, 1L, UPDATE_TIME);
    }

    @Test
    void omittedDescriptionClearsPausedApplianceWithoutChangingLifecycleState()
            throws Exception {
        UUID id = id(4);
        applianceRepository.insert(appliance(
                id, "Paused appliance", "Description", CollectionState.PAUSED));
        Map<String, Object> before = row(id);

        putMetadata(id, "{\"displayName\":\"Paused renamed\"}")
                .andExpect(jsonPath("$.displayName").value("Paused renamed"))
                .andExpect(jsonPath("$.description").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.collectionState").value("PAUSED"))
                .andExpect(jsonPath("$.nextCollectionDueAt")
                        .value(org.hamcrest.Matchers.nullValue()));

        Map<String, Object> changed = row(id);
        assertThat(changed.get("collection_state")).isEqualTo("PAUSED");
        assertThat(changed.get("next_collection_due_at")).isNull();
        assertThat(changed.get("version")).isEqualTo(1L);
        assertNonMetadataUnchanged(before, changed);
    }

    @Test
    void missingApplianceReturnsFeatureNotFoundAndCreatesNoRow() throws Exception {
        UUID missingId = id(99);

        mockMvc.perform(put("/api/v1/appliances/{applianceId}/metadata", missingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Missing\"}"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("APPLIANCE_NOT_FOUND"));

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM appliance", Long.class))
                .isZero();
    }

    private org.springframework.test.web.servlet.ResultActions putMetadata(
            UUID id, String requestBody) throws Exception {
        return mockMvc.perform(put("/api/v1/appliances/{applianceId}/metadata", id)
                        .header(CorrelationIdConstants.HEADER_NAME, CORRELATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(header().string(CorrelationIdConstants.HEADER_NAME, CORRELATION_ID))
                .andExpect(jsonPath("$.version").doesNotExist());
    }

    private Appliance appliance(
            UUID id, String displayName, String description, CollectionState state) {
        return new Appliance(
                id,
                displayName,
                description,
                "mock-alpha",
                "metadata-ref-" + id,
                state,
                45,
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

    private void assertVersionAndTime(UUID id, long version, Instant updatedAt) {
        Map<String, Object> row = row(id);
        assertThat(row.get("version")).isEqualTo(version);
        assertThat(databaseInstant(row.get("updated_at"))).isEqualTo(updatedAt);
    }

    private void assertNonMetadataUnchanged(
            Map<String, Object> before, Map<String, Object> after) {
        for (String column : new String[] {
                "id",
                "vendor_key",
                "external_reference",
                "collection_state",
                "collection_interval_seconds",
                "next_collection_due_at",
                "consecutive_failure_count",
                "last_collection_status",
                "created_at"
        }) {
            assertThat(after.get(column)).as(column).isEqualTo(before.get(column));
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
        return UUID.fromString("00000000-0000-0000-0000-" + String.format("%012d", suffix));
    }
}
