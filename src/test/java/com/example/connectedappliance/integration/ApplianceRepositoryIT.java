package com.example.connectedappliance.integration;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.example.connectedappliance.appliance.application.port.out.AppliancePage;
import com.example.connectedappliance.appliance.application.port.out.AppliancePageRequest;
import com.example.connectedappliance.appliance.application.port.out.ApplianceRepository;
import com.example.connectedappliance.appliance.domain.Appliance;
import com.example.connectedappliance.appliance.domain.CollectionState;
import com.example.connectedappliance.appliance.domain.LastCollectionStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Execution(ExecutionMode.SAME_THREAD)
class ApplianceRepositoryIT extends PostgresIntegrationTestSupport {

    private static final Instant BASE_TIME = Instant.parse("2026-07-21T10:00:00.123456Z");

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
    void insertsAssignedUuidAndReturnsTheActualInitializedVersionWithoutMerging() {
        UUID assignedId = id(1);
        Appliance appliance = appliance(
                assignedId,
                "initial-version",
                CollectionState.ACTIVE,
                BASE_TIME,
                BASE_TIME.plusSeconds(30));

        Appliance inserted = applianceRepository.insert(appliance);
        Long databaseVersion = jdbcTemplate.queryForObject(
                "SELECT version FROM appliance WHERE id = ?", Long.class, assignedId);

        assertThat(inserted.id()).isEqualTo(assignedId);
        assertThat(databaseVersion).isNotNull();
        assertThat(inserted.version()).isEqualTo(databaseVersion.longValue());
        assertThat(databaseVersion).isZero();

        Appliance sameId = appliance(
                assignedId,
                "second-insert",
                CollectionState.ACTIVE,
                BASE_TIME.plusSeconds(1),
                BASE_TIME.plusSeconds(31));
        assertThatThrownBy(() -> applianceRepository.insert(sameId))
                .isInstanceOf(RuntimeException.class);

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT external_reference FROM appliance WHERE id = ?",
                        String.class,
                        assignedId))
                .isEqualTo("initial-version");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM appliance", Long.class))
                .isOne();
    }

    @Test
    void roundTripsAllThirteenFieldsAndFindsByAssignedUuid() {
        UUID assignedId = id(2);
        Appliance original = new Appliance(
                assignedId,
                "  Round-trip appliance  ",
                "  Nullable description is populated here  ",
                "mock-alpha",
                "  MixedCase/Reference-Aa  ",
                CollectionState.ACTIVE,
                86_400,
                BASE_TIME.plusSeconds(86_400),
                3,
                LastCollectionStatus.FAILED,
                0,
                BASE_TIME,
                BASE_TIME.plusSeconds(5));

        Appliance inserted = applianceRepository.insert(original);
        Appliance found = applianceRepository.findById(assignedId).orElseThrow();

        assertSameFields(inserted, found);
        assertThat(found.id()).isEqualTo(assignedId);
        assertThat(found.displayName()).isEqualTo(original.displayName());
        assertThat(found.description()).isEqualTo(original.description());
        assertThat(found.vendorKey()).isEqualTo(original.vendorKey());
        assertThat(found.externalReference()).isEqualTo(original.externalReference());
        assertThat(found.collectionState()).isEqualTo(original.collectionState());
        assertThat(found.collectionIntervalSeconds())
                .isEqualTo(original.collectionIntervalSeconds());
        assertThat(found.nextCollectionDueAt()).isEqualTo(original.nextCollectionDueAt());
        assertThat(found.consecutiveFailureCount()).isEqualTo(original.consecutiveFailureCount());
        assertThat(found.lastCollectionStatus()).isEqualTo(original.lastCollectionStatus());
        assertThat(found.version()).isEqualTo(inserted.version());
        assertThat(found.createdAt()).isEqualTo(original.createdAt());
        assertThat(found.updatedAt()).isEqualTo(original.updatedAt());

        Appliance nullableDescription = appliance(
                id(3),
                "nullable-description",
                CollectionState.PAUSED,
                BASE_TIME.plusSeconds(10),
                null);
        applianceRepository.insert(nullableDescription);
        assertThat(applianceRepository.findById(nullableDescription.id()).orElseThrow().description())
                .isNull();
        assertThat(applianceRepository.findById(id(99))).isEmpty();
        assertThatThrownBy(() -> applianceRepository.findById(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void listsWithDeterministicPaginationAndOptionalStateFiltersWithoutMutation() {
        Appliance first = appliance(id(10), "first", CollectionState.ACTIVE, BASE_TIME, BASE_TIME);
        Appliance second = appliance(id(20), "second", CollectionState.PAUSED, BASE_TIME, null);
        Appliance third = appliance(
                id(30),
                "third",
                CollectionState.ACTIVE,
                BASE_TIME.plusSeconds(1),
                BASE_TIME.plusSeconds(1));
        Appliance fourth = appliance(
                id(40),
                "fourth",
                CollectionState.PAUSED,
                BASE_TIME.plusSeconds(2),
                null);

        applianceRepository.insert(fourth);
        applianceRepository.insert(second);
        applianceRepository.insert(third);
        applianceRepository.insert(first);
        List<Map<String, Object>> before = applianceSnapshot();

        AppliancePage firstPage =
                applianceRepository.findAll(new AppliancePageRequest(0, 2), Optional.empty());
        AppliancePage secondPage =
                applianceRepository.findAll(new AppliancePageRequest(1, 2), Optional.empty());
        AppliancePage emptyPage =
                applianceRepository.findAll(new AppliancePageRequest(2, 2), Optional.empty());
        AppliancePage activePage = applianceRepository.findAll(
                new AppliancePageRequest(0, 10), Optional.of(CollectionState.ACTIVE));
        AppliancePage pausedPage = applianceRepository.findAll(
                new AppliancePageRequest(0, 10), Optional.of(CollectionState.PAUSED));

        assertPage(firstPage, 0, 2, 4, 2, List.of(first.id(), second.id()));
        assertPage(secondPage, 1, 2, 4, 2, List.of(third.id(), fourth.id()));
        assertPage(emptyPage, 2, 2, 4, 2, List.of());
        assertThat(activePage.items()).extracting(Appliance::id)
                .containsExactly(first.id(), third.id());
        assertThat(activePage.items()).extracting(Appliance::collectionState)
                .containsOnly(CollectionState.ACTIVE);
        assertThat(pausedPage.items()).extracting(Appliance::id)
                .containsExactly(second.id(), fourth.id());
        assertThat(pausedPage.items()).extracting(Appliance::collectionState)
                .containsOnly(CollectionState.PAUSED);
        assertThat(applianceSnapshot()).isEqualTo(before);
    }

    @Test
    void findsActiveAppliancesDueAtOrBeforeCutoffInFixedOrderAndRespectsLimit() {
        Instant cutoff = BASE_TIME.plusSeconds(100);
        Appliance beforeCutoff = appliance(
                id(10), "due-before", CollectionState.ACTIVE, BASE_TIME, cutoff.minusSeconds(2));
        Appliance sameDueLowerId = appliance(
                id(20), "same-due-lower", CollectionState.ACTIVE, BASE_TIME, cutoff.minusSeconds(1));
        Appliance sameDueHigherId = appliance(
                id(30), "same-due-higher", CollectionState.ACTIVE, BASE_TIME, cutoff.minusSeconds(1));
        Appliance exactlyAtCutoff = appliance(
                id(40), "due-exactly", CollectionState.ACTIVE, BASE_TIME, cutoff);
        Appliance future = appliance(
                id(50), "future", CollectionState.ACTIVE, BASE_TIME, cutoff.plusNanos(1_000));
        Appliance paused = appliance(
                id(60), "paused", CollectionState.PAUSED, BASE_TIME, null);

        for (Appliance appliance :
                List.of(future, paused, exactlyAtCutoff, sameDueHigherId, beforeCutoff, sameDueLowerId)) {
            applianceRepository.insert(appliance);
        }
        List<Map<String, Object>> before = applianceSnapshot();

        List<Appliance> due = applianceRepository.findDue(cutoff, 10);
        List<Appliance> limited = applianceRepository.findDue(cutoff, 2);

        assertThat(due).extracting(Appliance::id).containsExactly(
                beforeCutoff.id(),
                sameDueLowerId.id(),
                sameDueHigherId.id(),
                exactlyAtCutoff.id());
        assertThat(due).extracting(Appliance::collectionState)
                .containsOnly(CollectionState.ACTIVE);
        assertThat(limited).extracting(Appliance::id)
                .containsExactly(beforeCutoff.id(), sameDueLowerId.id());
        assertThat(applianceSnapshot()).isEqualTo(before);

        assertThat(rootCause(catchThrowable(() -> applianceRepository.findDue(null, 1))))
                .isInstanceOf(NullPointerException.class);
        assertThat(rootCause(catchThrowable(() -> applianceRepository.findDue(cutoff, 0))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(rootCause(catchThrowable(() -> applianceRepository.findDue(cutoff, -1))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Appliance appliance(
            UUID id,
            String externalReference,
            CollectionState state,
            Instant createdAt,
            Instant nextDueAt) {
        return new Appliance(
                id,
                "Repository appliance " + externalReference,
                null,
                "mock-alpha",
                externalReference,
                state,
                30,
                nextDueAt,
                0,
                LastCollectionStatus.NEVER_ATTEMPTED,
                0,
                createdAt,
                createdAt);
    }

    private UUID id(int value) {
        return UUID.fromString("00000000-0000-0000-0000-" + String.format("%012d", value));
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

    private void assertPage(
            AppliancePage page,
            int expectedPage,
            int expectedSize,
            long expectedTotalElements,
            int expectedTotalPages,
            List<UUID> expectedIds) {
        assertThat(page.page()).isEqualTo(expectedPage);
        assertThat(page.size()).isEqualTo(expectedSize);
        assertThat(page.totalElements()).isEqualTo(expectedTotalElements);
        assertThat(page.totalPages()).isEqualTo(expectedTotalPages);
        assertThat(page.items()).extracting(Appliance::id).containsExactlyElementsOf(expectedIds);
    }

    private void assertSameFields(Appliance expected, Appliance actual) {
        assertThat(actual.id()).isEqualTo(expected.id());
        assertThat(actual.displayName()).isEqualTo(expected.displayName());
        assertThat(actual.description()).isEqualTo(expected.description());
        assertThat(actual.vendorKey()).isEqualTo(expected.vendorKey());
        assertThat(actual.externalReference()).isEqualTo(expected.externalReference());
        assertThat(actual.collectionState()).isEqualTo(expected.collectionState());
        assertThat(actual.collectionIntervalSeconds()).isEqualTo(expected.collectionIntervalSeconds());
        assertThat(actual.nextCollectionDueAt()).isEqualTo(expected.nextCollectionDueAt());
        assertThat(actual.consecutiveFailureCount()).isEqualTo(expected.consecutiveFailureCount());
        assertThat(actual.lastCollectionStatus()).isEqualTo(expected.lastCollectionStatus());
        assertThat(actual.version()).isEqualTo(expected.version());
        assertThat(actual.createdAt()).isEqualTo(expected.createdAt());
        assertThat(actual.updatedAt()).isEqualTo(expected.updatedAt());
    }

    private Throwable rootCause(Throwable failure) {
        assertThat(failure).isNotNull();
        Throwable current = failure;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
