package com.example.connectedappliance.integration;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.example.connectedappliance.appliance.application.port.out.ApplianceRepository;
import com.example.connectedappliance.appliance.domain.Appliance;
import com.example.connectedappliance.appliance.domain.CollectionState;
import com.example.connectedappliance.appliance.domain.LastCollectionStatus;
import com.example.connectedappliance.shared.observability.CorrelationIdConstants;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Execution(ExecutionMode.SAME_THREAD)
class ApplianceListingIT extends PostgresIntegrationTestSupport {

    private static final Instant BASE_TIME = Instant.parse("2026-07-21T10:00:00.123456Z");
    private static final String CORRELATION_ID = "task11-integration";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ApplianceRepository applianceRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void removeApplianceRows() {
        jdbcTemplate.update("DELETE FROM appliance");
    }

    @Test
    void listsInCreatedAtThenIdOrderWithoutMutatingStoredAppliances() throws Exception {
        Appliance first = appliance(10, "first", CollectionState.ACTIVE, BASE_TIME);
        Appliance second = appliance(20, "second", CollectionState.PAUSED, BASE_TIME);
        Appliance third = appliance(
                30, "third", CollectionState.ACTIVE, BASE_TIME.plusNanos(1_000));
        Appliance fourth = appliance(
                40, "fourth", CollectionState.PAUSED, BASE_TIME.plusSeconds(1));
        insert(fourth, second, third, first);
        List<Map<String, Object>> before = applianceSnapshot();

        var result = mockMvc.perform(get("/api/v1/appliances")
                        .queryParam("page", "0")
                        .queryParam("size", "20")
                        .header(CorrelationIdConstants.HEADER_NAME, CORRELATION_ID))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(header().string(CorrelationIdConstants.HEADER_NAME, CORRELATION_ID))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.items[0].version").doesNotExist())
                .andExpect(jsonPath("$.items[0].id").value(first.id().toString()))
                .andExpect(jsonPath("$.items[1].id").value(second.id().toString()))
                .andExpect(jsonPath("$.items[2].id").value(third.id().toString()))
                .andExpect(jsonPath("$.items[3].id").value(fourth.id().toString()))
                .andReturn();

        JsonNode response = json(result);
        assertThat(fieldNames(response)).containsExactly(
                "items", "page", "size", "totalElements", "totalPages");
        assertThat(response.toString()).doesNotContain(
                "version", "hibernateLazyInitializer", "_links", "pageable", "sort");
        assertThat(applianceSnapshot()).isEqualTo(before);
    }

    @Test
    void paginatesAcrossBoundariesWithoutDuplicatesOrMissingRows() throws Exception {
        List<Appliance> expected = List.of(
                appliance(1, "page-1", CollectionState.ACTIVE, BASE_TIME),
                appliance(2, "page-2", CollectionState.PAUSED, BASE_TIME),
                appliance(3, "page-3", CollectionState.ACTIVE, BASE_TIME.plusSeconds(1)),
                appliance(4, "page-4", CollectionState.PAUSED, BASE_TIME.plusSeconds(2)),
                appliance(5, "page-5", CollectionState.ACTIVE, BASE_TIME.plusSeconds(3)));
        insert(expected.toArray(Appliance[]::new));

        JsonNode firstPage = page(0, 2, null);
        JsonNode secondPage = page(1, 2, null);
        JsonNode thirdPage = page(2, 2, null);

        assertPage(firstPage, 0, 2, 5, 3, List.of(expected.get(0).id(), expected.get(1).id()));
        assertPage(secondPage, 1, 2, 5, 3, List.of(expected.get(2).id(), expected.get(3).id()));
        assertPage(thirdPage, 2, 2, 5, 3, List.of(expected.get(4).id()));

        List<UUID> allIds = new ArrayList<>();
        allIds.addAll(ids(firstPage));
        allIds.addAll(ids(secondPage));
        allIds.addAll(ids(thirdPage));
        assertThat(allIds).containsExactlyElementsOf(expected.stream().map(Appliance::id).toList());
        assertThat(new LinkedHashSet<>(allIds)).hasSize(expected.size());
    }

    @Test
    void filtersActiveAndPausedWithIndependentTotalsAndFixedOrder() throws Exception {
        Appliance activeFirst = appliance(10, "active-first", CollectionState.ACTIVE, BASE_TIME);
        Appliance pausedFirst = appliance(20, "paused-first", CollectionState.PAUSED, BASE_TIME);
        Appliance activeSecond = appliance(
                30, "active-second", CollectionState.ACTIVE, BASE_TIME.plusSeconds(1));
        Appliance pausedSecond = appliance(
                40, "paused-second", CollectionState.PAUSED, BASE_TIME.plusSeconds(1));
        insert(pausedSecond, activeSecond, pausedFirst, activeFirst);

        JsonNode activePage = page(0, 20, CollectionState.ACTIVE);
        JsonNode pausedPage = page(0, 20, CollectionState.PAUSED);

        assertPage(activePage, 0, 20, 2, 1, List.of(activeFirst.id(), activeSecond.id()));
        assertPage(pausedPage, 0, 20, 2, 1, List.of(pausedFirst.id(), pausedSecond.id()));
        activePage.path("items").forEach(
                item -> assertThat(item.path("collectionState").asText()).isEqualTo("ACTIVE"));
        pausedPage.path("items").forEach(
                item -> assertThat(item.path("collectionState").asText()).isEqualTo("PAUSED"));
    }

    @Test
    void returnsApprovedEmptyPageForEmptyDatabase() throws Exception {
        JsonNode response = page(0, 20, null);

        assertPage(response, 0, 20, 0, 0, List.of());
    }

    @Test
    void returnsEmptyBeyondFinalPageWhileRetainingActualTotals() throws Exception {
        insert(
                appliance(1, "beyond-1", CollectionState.ACTIVE, BASE_TIME),
                appliance(2, "beyond-2", CollectionState.PAUSED, BASE_TIME.plusSeconds(1)),
                appliance(3, "beyond-3", CollectionState.ACTIVE, BASE_TIME.plusSeconds(2)));

        JsonNode response = page(5, 2, null);

        assertPage(response, 5, 2, 3, 2, List.of());
    }

    private JsonNode page(int page, int size, CollectionState state) throws Exception {
        var request = get("/api/v1/appliances")
                .queryParam("page", Integer.toString(page))
                .queryParam("size", Integer.toString(size))
                .header(CorrelationIdConstants.HEADER_NAME, CORRELATION_ID);
        if (state != null) {
            request.queryParam("collectionState", state.name());
        }
        var result = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(header().string(CorrelationIdConstants.HEADER_NAME, CORRELATION_ID))
                .andReturn();
        return json(result);
    }

    private JsonNode json(org.springframework.test.web.servlet.MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private void assertPage(
            JsonNode response,
            int page,
            int size,
            long totalElements,
            int totalPages,
            List<UUID> expectedIds) {
        assertThat(response.path("page").asInt()).isEqualTo(page);
        assertThat(response.path("size").asInt()).isEqualTo(size);
        assertThat(response.path("totalElements").asLong()).isEqualTo(totalElements);
        assertThat(response.path("totalPages").asInt()).isEqualTo(totalPages);
        assertThat(ids(response)).containsExactlyElementsOf(expectedIds);
    }

    private List<UUID> ids(JsonNode response) {
        List<UUID> ids = new ArrayList<>();
        response.path("items").forEach(item -> ids.add(UUID.fromString(item.path("id").asText())));
        return ids;
    }

    private void insert(Appliance... appliances) {
        for (Appliance appliance : appliances) {
            applianceRepository.insert(appliance);
        }
    }

    private Appliance appliance(
            int numericId, String externalReference, CollectionState state, Instant createdAt) {
        UUID id = UUID.fromString(
                "00000000-0000-0000-0000-" + String.format("%012d", numericId));
        return new Appliance(
                id,
                "Listing appliance " + externalReference,
                "Listing integration description",
                "mock-alpha",
                externalReference,
                state,
                30,
                state == CollectionState.ACTIVE ? createdAt.plusSeconds(30) : null,
                numericId,
                numericId % 2 == 0
                        ? LastCollectionStatus.SUCCESS
                        : LastCollectionStatus.NEVER_ATTEMPTED,
                0,
                createdAt,
                createdAt.plusNanos(1_000));
    }

    private List<Map<String, Object>> applianceSnapshot() {
        return jdbcTemplate.queryForList(
                """
                SELECT id,
                       display_name,
                       description,
                       vendor_key,
                       external_reference,
                       collection_state,
                       collection_interval_seconds,
                       next_collection_due_at,
                       consecutive_failure_count,
                       last_collection_status,
                       version,
                       created_at,
                       updated_at
                FROM appliance
                ORDER BY id
                """);
    }

    private Set<String> fieldNames(JsonNode node) {
        Set<String> names = new TreeSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
