package com.example.connectedappliance.integration;

import java.time.Instant;
import java.util.LinkedHashMap;
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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.example.connectedappliance.appliance.application.exception.DuplicateApplianceException;
import com.example.connectedappliance.appliance.application.port.out.ApplianceRepository;
import com.example.connectedappliance.appliance.domain.Appliance;
import com.example.connectedappliance.appliance.domain.CollectionState;
import com.example.connectedappliance.appliance.domain.LastCollectionStatus;
import com.example.connectedappliance.shared.observability.CorrelationIdConstants;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(Task10FixedClockConfiguration.class)
@Execution(ExecutionMode.SAME_THREAD)
class ApplianceRegistrationIT extends PostgresIntegrationTestSupport {

    private static final Instant REGISTRATION_TIME =
            Task10FixedClockConfiguration.REGISTRATION_TIME;
    private static final String CORRELATION_ID = "task10-integration";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ApplianceRepository applianceRepository;

    @BeforeEach
    @AfterEach
    void removeApplianceRows() {
        jdbcTemplate.update("DELETE FROM appliance");
    }

    @Test
    void registersThenRetrievesApprovedRepresentationThroughRealPostgresql() throws Exception {
        String externalReference = "MixedCase/Reviewer-Ref";
        String request = registrationRequest(
                "  Kitchen appliance  ",
                "  Swagger verification appliance  ",
                "mock-alpha",
                externalReference,
                30);

        var registrationResult = mockMvc.perform(post("/api/v1/appliances")
                        .header(CorrelationIdConstants.HEADER_NAME, CORRELATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(header().string(CorrelationIdConstants.HEADER_NAME, CORRELATION_ID))
                .andExpect(jsonPath("$.displayName").value("Kitchen appliance"))
                .andExpect(jsonPath("$.description").value("Swagger verification appliance"))
                .andExpect(jsonPath("$.vendorKey").value("mock-alpha"))
                .andExpect(jsonPath("$.externalReference").value(externalReference))
                .andExpect(jsonPath("$.collectionState").value("ACTIVE"))
                .andExpect(jsonPath("$.collectionIntervalSeconds").value(30))
                .andExpect(jsonPath("$.nextCollectionDueAt").value("2026-07-21T10:00:30Z"))
                .andExpect(jsonPath("$.consecutiveFailureCount").value(0))
                .andExpect(jsonPath("$.lastCollectionStatus").value("NEVER_ATTEMPTED"))
                .andExpect(jsonPath("$.createdAt").value("2026-07-21T10:00:00Z"))
                .andExpect(jsonPath("$.updatedAt").value("2026-07-21T10:00:00Z"))
                .andExpect(jsonPath("$.version").doesNotExist())
                .andReturn();

        JsonNode registered = objectMapper.readTree(
                registrationResult.getResponse().getContentAsString());
        UUID id = UUID.fromString(registered.path("id").asText());
        String location = registrationResult.getResponse().getHeader("Location");
        assertThat(location).isEqualTo("/api/v1/appliances/" + id);

        var retrievalResult = mockMvc.perform(get(location)
                        .header(CorrelationIdConstants.HEADER_NAME, CORRELATION_ID))
                .andExpect(status().isOk())
                .andExpect(header().string(CorrelationIdConstants.HEADER_NAME, CORRELATION_ID))
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.version").doesNotExist())
                .andExpect(jsonPath("$.temp_c").doesNotExist())
                .andExpect(jsonPath("$.power_w").doesNotExist())
                .andReturn();

        assertThat(objectMapper.readTree(retrievalResult.getResponse().getContentAsString()))
                .isEqualTo(registered);
        Map<String, Object> row = jdbcTemplate.queryForMap(
                """
                SELECT id, display_name, description, vendor_key, external_reference,
                       collection_state, collection_interval_seconds, next_collection_due_at,
                       consecutive_failure_count, last_collection_status, version,
                       created_at, updated_at
                FROM appliance
                WHERE id = ?
                """,
                id);
        assertThat(row.get("id")).isEqualTo(id);
        assertThat(row.get("display_name")).isEqualTo("Kitchen appliance");
        assertThat(row.get("description")).isEqualTo("Swagger verification appliance");
        assertThat(row.get("external_reference")).isEqualTo(externalReference);
        assertThat(row.get("version")).isEqualTo(0L);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM appliance", Long.class))
                .isOne();
    }

    @Test
    void rejectsExactDuplicateAndPreservesTheFirstRow() throws Exception {
        String externalReference = "duplicate-reference";
        String firstRequest = registrationRequest(
                "First appliance", null, "mock-alpha", externalReference, 30);
        String duplicateRequest = registrationRequest(
                "Replacement attempt", null, "mock-alpha", externalReference, 60);

        mockMvc.perform(post("/api/v1/appliances")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstRequest))
                .andExpect(status().isCreated());

        String response = mockMvc.perform(post("/api/v1/appliances")
                        .header(CorrelationIdConstants.HEADER_NAME, CORRELATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(duplicateRequest))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("DUPLICATE_APPLIANCE"))
                .andExpect(jsonPath("$.correlationId").value(CORRELATION_ID))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(response).doesNotContain(
                externalReference,
                "uk_appliance_vendor_key_external_reference",
                "SQLException",
                "ConstraintViolationException");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM appliance WHERE vendor_key = ? AND external_reference = ?",
                        Long.class,
                        "mock-alpha",
                        externalReference))
                .isOne();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT display_name FROM appliance WHERE vendor_key = ? AND external_reference = ?",
                        String.class,
                        "mock-alpha",
                        externalReference))
                .isEqualTo("First appliance");
    }

    @Test
    void acceptsCaseDistinctOpaqueExternalReferences() throws Exception {
        for (String externalReference : new String[] {"CaseSensitive-A", "casesensitive-a"}) {
            mockMvc.perform(post("/api/v1/appliances")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(registrationRequest(
                                    "Appliance " + externalReference,
                                    null,
                                    "mock-alpha",
                                    externalReference,
                                    30)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.externalReference").value(externalReference));
        }

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM appliance", Long.class))
                .isEqualTo(2L);
    }

    @Test
    void rejectsUnsupportedVendorWithoutCreatingRowOrEchoingKey() throws Exception {
        String unsupportedKey = "unknown-vendor";

        String response = mockMvc.perform(post("/api/v1/appliances")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationRequest(
                                "Unsupported appliance",
                                null,
                                unsupportedKey,
                                "unsupported-ref",
                                30)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_VENDOR"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(response).doesNotContain(unsupportedKey);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM appliance", Long.class))
                .isZero();
    }

    @Test
    void returnsFeatureNotFoundForValidMissingIdentifier() throws Exception {
        UUID missingId = UUID.fromString("00000000-0000-0000-0000-000000000099");

        mockMvc.perform(get("/api/v1/appliances/{applianceId}", missingId))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("APPLIANCE_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value(
                        "No appliance exists with the supplied identifier."));
    }

    @Test
    void doesNotTranslatePrimaryKeyCollisionAsVendorIdentityDuplicate() {
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000077");
        applianceRepository.insert(appliance(id, "first-identity"));

        Throwable failure = catchThrowable(
                () -> applianceRepository.insert(appliance(id, "second-identity")));

        assertThat(failure).isNotNull();
        assertThat(causeChainContains(failure, DuplicateApplianceException.class)).isFalse();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM appliance", Long.class))
                .isOne();
    }

    private Appliance appliance(UUID id, String externalReference) {
        return new Appliance(
                id,
                "Primary collision appliance",
                null,
                "mock-alpha",
                externalReference,
                CollectionState.ACTIVE,
                30,
                REGISTRATION_TIME.plusSeconds(30),
                0,
                LastCollectionStatus.NEVER_ATTEMPTED,
                0,
                REGISTRATION_TIME,
                REGISTRATION_TIME);
    }

    private boolean causeChainContains(Throwable failure, Class<? extends Throwable> type) {
        Throwable current = failure;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String registrationRequest(
            String displayName,
            String description,
            String vendorKey,
            String externalReference,
            int interval) throws Exception {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("displayName", displayName);
        request.put("description", description);
        request.put("vendorKey", vendorKey);
        request.put("externalReference", externalReference);
        request.put("collectionIntervalSeconds", interval);
        return objectMapper.writeValueAsString(request);
    }
}
