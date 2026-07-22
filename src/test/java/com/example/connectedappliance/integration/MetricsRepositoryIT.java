package com.example.connectedappliance.integration;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.example.connectedappliance.metrics.application.port.out.CollectionAttemptPage;
import com.example.connectedappliance.metrics.application.port.out.CollectionAttemptQuery;
import com.example.connectedappliance.metrics.application.port.out.HistoryPageRequest;
import com.example.connectedappliance.metrics.application.port.out.MetricSamplePage;
import com.example.connectedappliance.metrics.application.port.out.MetricSampleQuery;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Execution(ExecutionMode.SAME_THREAD)
class MetricsRepositoryIT extends PostgresIntegrationTestSupport {

    private static final Instant BASE = Instant.parse("2026-07-21T10:00:00Z");
    private static final CollectionWarning UNKNOWN_WARNING =
            new CollectionWarning("UNKNOWN_METRIC", "Unsupported reading omitted");
    private static final CollectionWarning MALFORMED_WARNING =
            new CollectionWarning("MALFORMED_VALUE", "Malformed reading omitted");

    @Autowired
    private MetricsRepository metricsRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM metric_sample");
        jdbcTemplate.update("DELETE FROM collection_warning");
        jdbcTemplate.update("DELETE FROM collection_attempt");
        jdbcTemplate.update("DELETE FROM appliance");
    }

    @Test
    void insertsSuccessAttemptAndSamplesAtomicallyWithoutChangingAppliance() {
        UUID applianceId = insertAppliance("atomic-success");
        UUID attemptId = UUID.randomUUID();
        UUID temperatureId = uuid(401);
        UUID powerId = uuid(402);
        Instant due = BASE.plusSeconds(32);
        ApplianceSnapshot before = applianceSnapshot(applianceId);
        CompletedCollection collection = success(
                applianceId,
                attemptId,
                BASE,
                due,
                List.of(
                        sample(temperatureId, applianceId, attemptId, CanonicalMetric.TEMPERATURE,
                                CanonicalUnit.CELSIUS, "21.500000", BASE.plusSeconds(1), BASE.plusSeconds(2)),
                        sample(powerId, applianceId, attemptId, CanonicalMetric.POWER,
                                CanonicalUnit.WATT, "125.000000", BASE.plusSeconds(1), BASE.plusSeconds(2))));

        CollectionAttempt returned = metricsRepository.insert(collection);

        assertThat(returned).isEqualTo(collection.attempt());
        assertThat(count("collection_attempt", "id", attemptId)).isEqualTo(1);
        assertThat(count("collection_warning", "collection_attempt_id", attemptId)).isZero();
        assertThat(count("metric_sample", "collection_attempt_id", attemptId)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT sample_count FROM collection_attempt WHERE id = ?", Integer.class, attemptId))
                .isEqualTo(2);
        assertThat(applianceSnapshot(applianceId)).isEqualTo(before);

        MetricSamplePage stored = metricsRepository.findMetricSamples(new MetricSampleQuery(
                applianceId, BASE, BASE.plusSeconds(10), new HistoryPageRequest(0, 10)));
        assertThat(stored.items()).extracting(MetricSample::id)
                .containsExactly(temperatureId, powerId);
        assertThat(stored.items()).extracting(MetricSample::value)
                .containsExactly(new BigDecimal("21.500000"), new BigDecimal("125.000000"));
        assertThat(stored.items()).extracting(MetricSample::ingestedAt)
                .containsOnly(BASE.plusSeconds(2));
        assertThat(returned.nextCollectionDueAt()).isEqualTo(due);
    }

    @Test
    void roundTripsPartialAndFailedAttemptsWithOrderedWarningsAndFailureFields() {
        UUID applianceId = insertAppliance("partial-failed");
        UUID partialId = UUID.randomUUID();
        CompletedCollection partial = partial(
                applianceId,
                partialId,
                BASE.plusSeconds(10),
                BASE.plusSeconds(42),
                List.of(UNKNOWN_WARNING, MALFORMED_WARNING),
                List.of(sample(
                        UUID.randomUUID(), applianceId, partialId, CanonicalMetric.POWER,
                        CanonicalUnit.WATT, "125", BASE.plusSeconds(11), BASE.plusSeconds(12))));
        UUID failedId = UUID.randomUUID();
        CollectionFailure failure =
                new CollectionFailure(CollectionFailureCategory.RATE_LIMITED, "Retry later", 45);
        CompletedCollection failed = failed(
                applianceId,
                failedId,
                BASE.plusSeconds(20),
                null,
                List.of(MALFORMED_WARNING),
                failure);

        metricsRepository.insert(partial);
        metricsRepository.insert(failed);
        CollectionAttemptPage history = attempts(applianceId, Optional.empty(), Optional.empty(), 0, 10);

        assertThat(history.items()).extracting(CollectionAttempt::id)
                .containsExactly(failedId, partialId);
        assertThat(history.items().get(1).warnings())
                .containsExactly(UNKNOWN_WARNING, MALFORMED_WARNING);
        assertThat(jdbcTemplate.queryForList(
                        """
                        SELECT warning_index FROM collection_warning
                        WHERE collection_attempt_id = ? ORDER BY warning_index
                        """,
                        Integer.class,
                        partialId))
                .containsExactly(0, 1);
        CollectionAttempt storedFailure = history.items().get(0);
        assertThat(storedFailure.failure()).isEqualTo(failure);
        assertThat(storedFailure.nextCollectionDueAt()).isNull();
        assertThat(count("metric_sample", "collection_attempt_id", failedId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT failure_category FROM collection_attempt WHERE id = ?", String.class, failedId))
                .isEqualTo("RATE_LIMITED");
    }

    @Test
    void rollsBackAttemptWarningsAndAllNewSamplesWhenOneSampleConflicts() {
        UUID applianceId = insertAppliance("rollback");
        UUID existingAttemptId = UUID.randomUUID();
        UUID conflictingSampleId = UUID.randomUUID();
        metricsRepository.insert(success(
                applianceId,
                existingAttemptId,
                BASE,
                null,
                List.of(sample(
                        conflictingSampleId, applianceId, existingAttemptId,
                        CanonicalMetric.POWER, CanonicalUnit.WATT, "1", BASE, BASE))));

        UUID newAttemptId = UUID.randomUUID();
        UUID nonConflictingSampleId = UUID.randomUUID();
        CompletedCollection conflicting = partial(
                applianceId,
                newAttemptId,
                BASE.plusSeconds(10),
                null,
                List.of(UNKNOWN_WARNING),
                List.of(
                        sample(nonConflictingSampleId, applianceId, newAttemptId,
                                CanonicalMetric.TEMPERATURE, CanonicalUnit.CELSIUS, "2", BASE, BASE),
                        sample(conflictingSampleId, applianceId, newAttemptId,
                                CanonicalMetric.POWER, CanonicalUnit.WATT, "3", BASE, BASE)));

        assertThatThrownBy(() -> metricsRepository.insert(conflicting))
                .isInstanceOf(RuntimeException.class);

        assertThat(count("collection_attempt", "id", newAttemptId)).isZero();
        assertThat(count("collection_warning", "collection_attempt_id", newAttemptId)).isZero();
        assertThat(count("metric_sample", "id", nonConflictingSampleId)).isZero();
        assertThat(count("metric_sample", "id", conflictingSampleId)).isEqualTo(1);
        assertThat(count("collection_attempt", "id", existingAttemptId)).isEqualTo(1);
    }

    @Test
    void duplicateAssignedAttemptIdFailsWithoutOverwritingExistingAttempt() {
        UUID applianceId = insertAppliance("duplicate-attempt");
        UUID attemptId = UUID.randomUUID();
        CompletedCollection original = success(
                applianceId,
                attemptId,
                BASE,
                null,
                List.of(sample(
                        UUID.randomUUID(), applianceId, attemptId, CanonicalMetric.POWER,
                        CanonicalUnit.WATT, "10", BASE, BASE)));
        metricsRepository.insert(original);
        CompletedCollection duplicate = success(
                applianceId,
                attemptId,
                BASE.plusSeconds(10),
                null,
                List.of(sample(
                        UUID.randomUUID(), applianceId, attemptId, CanonicalMetric.POWER,
                        CanonicalUnit.WATT, "99", BASE.plusSeconds(10), BASE.plusSeconds(10))));

        assertThatThrownBy(() -> metricsRepository.insert(duplicate))
                .isInstanceOf(RuntimeException.class);

        assertThat(count("collection_attempt", "id", attemptId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                                "SELECT started_at FROM collection_attempt WHERE id = ?",
                                OffsetDateTime.class,
                                attemptId)
                        .toInstant())
                .isEqualTo(BASE);
        assertThat(count("metric_sample", "collection_attempt_id", attemptId)).isEqualTo(1);
    }

    @Test
    void ordersAttemptHistoryAndAppliesEverySupportedFilterCombination() {
        UUID applianceId = insertAppliance("history");
        UUID otherApplianceId = insertAppliance("history-other");
        UUID latestLowerId = uuid(1);
        UUID latestHigherId = uuid(2);
        UUID partialId = uuid(3);
        UUID failedId = uuid(4);
        insertSingleSampleAttempt(applianceId, latestHigherId, BASE.plusSeconds(30),
                CollectionTrigger.SCHEDULED, CollectionOutcome.SUCCESS);
        insertSingleSampleAttempt(applianceId, latestLowerId, BASE.plusSeconds(30),
                CollectionTrigger.MANUAL, CollectionOutcome.SUCCESS);
        insertSingleSampleAttempt(applianceId, partialId, BASE.plusSeconds(20),
                CollectionTrigger.MANUAL, CollectionOutcome.PARTIAL_SUCCESS);
        metricsRepository.insert(failed(
                applianceId, failedId, BASE.plusSeconds(10), null, List.of(),
                new CollectionFailure(CollectionFailureCategory.TIMEOUT, null, null)));
        insertSingleSampleAttempt(otherApplianceId, uuid(5), BASE.plusSeconds(40),
                CollectionTrigger.MANUAL, CollectionOutcome.SUCCESS);

        assertThat(attempts(applianceId, Optional.empty(), Optional.empty(), 0, 10).items())
                .extracting(CollectionAttempt::id)
                .containsExactly(latestLowerId, latestHigherId, partialId, failedId);
        assertThat(attempts(applianceId, Optional.of(CollectionTrigger.MANUAL), Optional.empty(), 0, 10))
                .satisfies(page -> {
                    assertThat(page.items()).extracting(CollectionAttempt::id)
                            .containsExactly(latestLowerId, partialId);
                    assertThat(page.totalElements()).isEqualTo(2);
                });
        assertThat(attempts(applianceId, Optional.of(CollectionTrigger.SCHEDULED), Optional.empty(), 0, 10)
                        .items())
                .extracting(CollectionAttempt::id)
                .containsExactly(latestHigherId, failedId);
        assertThat(attempts(applianceId, Optional.empty(), Optional.of(CollectionOutcome.SUCCESS), 0, 10)
                        .items())
                .extracting(CollectionAttempt::id)
                .containsExactly(latestLowerId, latestHigherId);
        assertThat(attempts(applianceId, Optional.empty(), Optional.of(CollectionOutcome.PARTIAL_SUCCESS), 0, 10)
                        .items())
                .extracting(CollectionAttempt::id)
                .containsExactly(partialId);
        assertThat(attempts(applianceId, Optional.empty(), Optional.of(CollectionOutcome.FAILED), 0, 10)
                        .items())
                .extracting(CollectionAttempt::id)
                .containsExactly(failedId);
        CollectionAttemptPage combined = attempts(
                applianceId,
                Optional.of(CollectionTrigger.MANUAL),
                Optional.of(CollectionOutcome.PARTIAL_SUCCESS),
                0,
                10);
        assertThat(combined.items()).extracting(CollectionAttempt::id).containsExactly(partialId);
        assertThat(combined.totalElements()).isEqualTo(1);
        CollectionAttemptPage emptyCombined = attempts(
                applianceId,
                Optional.of(CollectionTrigger.SCHEDULED),
                Optional.of(CollectionOutcome.PARTIAL_SUCCESS),
                0,
                10);
        assertThat(emptyCombined.items()).isEmpty();
        assertThat(emptyCombined.totalElements()).isZero();
    }

    @Test
    void paginatesAttemptsAndHydratesOnlyTheirOrderedImmutableWarnings() {
        UUID applianceId = insertAppliance("attempt-pages");
        List<UUID> expectedOrder = new ArrayList<>();
        for (int index = 0; index < 5; index++) {
            UUID attemptId = uuid(20 + index);
            expectedOrder.add(0, attemptId);
            CompletedCollection collection = partial(
                    applianceId,
                    attemptId,
                    BASE.plusSeconds(index),
                    null,
                    List.of(
                            new CollectionWarning("FIRST_" + index, "first " + index),
                            new CollectionWarning("SECOND_" + index, "second " + index)),
                    List.of(sample(
                            uuid(100 + index), applianceId, attemptId, CanonicalMetric.POWER,
                            CanonicalUnit.WATT, Integer.toString(index), BASE, BASE)));
            metricsRepository.insert(collection);
        }

        CollectionAttemptPage page0 = attempts(applianceId, Optional.empty(), Optional.empty(), 0, 2);
        CollectionAttemptPage page1 = attempts(applianceId, Optional.empty(), Optional.empty(), 1, 2);
        CollectionAttemptPage page2 = attempts(applianceId, Optional.empty(), Optional.empty(), 2, 2);
        CollectionAttemptPage beyond = attempts(applianceId, Optional.empty(), Optional.empty(), 3, 2);
        List<UUID> allIds = new ArrayList<>();
        page0.items().forEach(item -> allIds.add(item.id()));
        page1.items().forEach(item -> allIds.add(item.id()));
        page2.items().forEach(item -> allIds.add(item.id()));

        assertThat(allIds).containsExactlyElementsOf(expectedOrder);
        assertThat(new HashSet<>(allIds)).hasSize(5);
        assertThat(page0.totalElements()).isEqualTo(5);
        assertThat(page0.totalPages()).isEqualTo(3);
        assertThat(page2.items()).hasSize(1);
        assertThat(beyond.items()).isEmpty();
        assertThat(beyond.page()).isEqualTo(3);
        assertThat(beyond.size()).isEqualTo(2);
        assertThat(beyond.totalElements()).isEqualTo(5);
        assertThat(beyond.totalPages()).isEqualTo(3);
        for (CollectionAttempt attempt : page0.items()) {
            assertThat(attempt.warnings()).hasSize(2);
            String suffix = attempt.warnings().get(0).code().substring("FIRST_".length());
            assertThat(attempt.warnings()).extracting(CollectionWarning::code)
                    .containsExactly("FIRST_" + suffix, "SECOND_" + suffix);
            assertThatThrownBy(() -> attempt.warnings().add(UNKNOWN_WARNING))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Test
    void preservesAttemptDueSnapshotsAfterCurrentApplianceDueChanges() {
        UUID applianceId = insertAppliance("due-snapshot");
        UUID dueAttemptId = UUID.randomUUID();
        UUID nullDueAttemptId = UUID.randomUUID();
        Instant storedDue = BASE.plusSeconds(60);
        metricsRepository.insert(success(
                applianceId,
                dueAttemptId,
                BASE,
                storedDue,
                List.of(sample(
                        UUID.randomUUID(), applianceId, dueAttemptId, CanonicalMetric.POWER,
                        CanonicalUnit.WATT, "1", BASE, BASE))));
        metricsRepository.insert(failed(
                applianceId,
                nullDueAttemptId,
                BASE.plusSeconds(10),
                null,
                List.of(),
                new CollectionFailure(CollectionFailureCategory.TRANSIENT, null, null)));
        Instant currentDue = BASE.plusSeconds(600);
        jdbcTemplate.update(
                "UPDATE appliance SET next_collection_due_at = ?, updated_at = ? WHERE id = ?",
                jdbcTime(currentDue),
                jdbcTime(BASE.plusSeconds(20)),
                applianceId);

        CollectionAttemptPage history = attempts(applianceId, Optional.empty(), Optional.empty(), 0, 10);
        CollectionAttempt dueAttempt = history.items().stream()
                .filter(attempt -> attempt.id().equals(dueAttemptId))
                .findFirst()
                .orElseThrow();
        CollectionAttempt nullDueAttempt = history.items().stream()
                .filter(attempt -> attempt.id().equals(nullDueAttemptId))
                .findFirst()
                .orElseThrow();

        assertThat(dueAttempt.nextCollectionDueAt()).isEqualTo(storedDue);
        assertThat(dueAttempt.nextCollectionDueAt()).isNotEqualTo(currentDue);
        assertThat(nullDueAttempt.nextCollectionDueAt()).isNull();
    }

    @Test
    void appliesObservedAtStartInclusiveAndEndExclusiveRangeBoundaries() {
        UUID applianceId = insertAppliance("range");
        UUID otherApplianceId = insertAppliance("range-other");
        Instant from = BASE.plusSeconds(100);
        Instant to = from.plusSeconds(10);
        UUID attemptId = UUID.randomUUID();
        List<MetricSample> samples = List.of(
                sample(uuid(201), applianceId, attemptId, CanonicalMetric.POWER, CanonicalUnit.WATT,
                        "1", from.minus(1, ChronoUnit.MICROS), from.plusSeconds(5)),
                sample(uuid(202), applianceId, attemptId, CanonicalMetric.POWER, CanonicalUnit.WATT,
                        "2", from, to.plusSeconds(100)),
                sample(uuid(203), applianceId, attemptId, CanonicalMetric.POWER, CanonicalUnit.WATT,
                        "3", from.plusSeconds(5), from.minusSeconds(100)),
                sample(uuid(204), applianceId, attemptId, CanonicalMetric.POWER, CanonicalUnit.WATT,
                        "4", to.minus(1, ChronoUnit.MICROS), from),
                sample(uuid(205), applianceId, attemptId, CanonicalMetric.POWER, CanonicalUnit.WATT,
                        "5", to, from),
                sample(uuid(206), applianceId, attemptId, CanonicalMetric.POWER, CanonicalUnit.WATT,
                        "6", to.plus(1, ChronoUnit.MICROS), from));
        metricsRepository.insert(success(applianceId, attemptId, BASE, null, samples));
        UUID otherAttempt = UUID.randomUUID();
        metricsRepository.insert(success(
                otherApplianceId,
                otherAttempt,
                BASE,
                null,
                List.of(sample(uuid(207), otherApplianceId, otherAttempt, CanonicalMetric.POWER,
                        CanonicalUnit.WATT, "7", from.plusSeconds(1), from))));

        MetricSamplePage page = metricsRepository.findMetricSamples(
                new MetricSampleQuery(applianceId, from, to, new HistoryPageRequest(0, 20)));

        assertThat(page.items()).extracting(MetricSample::id)
                .containsExactly(uuid(202), uuid(203), uuid(204));
        assertThat(page.totalElements()).isEqualTo(3);
        assertThat(page.items()).extracting(MetricSample::ingestedAt)
                .containsExactly(to.plusSeconds(100), from.minusSeconds(100), from);
    }

    @Test
    void ordersPaginatesAndPreservesDuplicateContentMetricSamples() {
        UUID applianceId = insertAppliance("metric-pages");
        UUID otherApplianceId = insertAppliance("metric-pages-other");
        Instant from = BASE.plusSeconds(200);
        Instant to = from.plusSeconds(20);
        UUID attemptId = UUID.randomUUID();
        List<MetricSample> samples = List.of(
                sample(uuid(302), applianceId, attemptId, CanonicalMetric.TEMPERATURE,
                        CanonicalUnit.CELSIUS, "21.500000", from, from),
                sample(uuid(301), applianceId, attemptId, CanonicalMetric.TEMPERATURE,
                        CanonicalUnit.CELSIUS, "21.500000", from, from),
                sample(uuid(303), applianceId, attemptId, CanonicalMetric.POWER,
                        CanonicalUnit.WATT, "125.000000", from.plusSeconds(1), from),
                sample(uuid(304), applianceId, attemptId, CanonicalMetric.POWER,
                        CanonicalUnit.WATT, "126.000000", from.plusSeconds(2), from),
                sample(uuid(305), applianceId, attemptId, CanonicalMetric.POWER,
                        CanonicalUnit.WATT, "127.000000", from.plusSeconds(3), from));
        metricsRepository.insert(success(applianceId, attemptId, BASE, null, samples));
        UUID otherAttempt = UUID.randomUUID();
        metricsRepository.insert(success(
                otherApplianceId,
                otherAttempt,
                BASE,
                null,
                List.of(sample(uuid(306), otherApplianceId, otherAttempt, CanonicalMetric.POWER,
                        CanonicalUnit.WATT, "500", from, from))));

        MetricSamplePage page0 = metrics(applianceId, from, to, 0, 2);
        MetricSamplePage page1 = metrics(applianceId, from, to, 1, 2);
        MetricSamplePage page2 = metrics(applianceId, from, to, 2, 2);
        MetricSamplePage beyond = metrics(applianceId, from, to, 3, 2);
        List<MetricSample> combined = new ArrayList<>();
        combined.addAll(page0.items());
        combined.addAll(page1.items());
        combined.addAll(page2.items());

        assertThat(combined).extracting(MetricSample::id)
                .containsExactly(uuid(301), uuid(302), uuid(303), uuid(304), uuid(305));
        assertThat(combined).extracting(MetricSample::value)
                .allMatch(value -> value.scale() == 6);
        assertThat(combined.get(0).metricName()).isEqualTo(CanonicalMetric.TEMPERATURE);
        assertThat(combined.get(0).unit()).isEqualTo(CanonicalUnit.CELSIUS);
        assertThat(combined.get(0).value()).isEqualTo(combined.get(1).value());
        assertThat(page0.totalElements()).isEqualTo(5);
        assertThat(page0.totalPages()).isEqualTo(3);
        assertThat(page2.items()).hasSize(1);
        assertThat(beyond.items()).isEmpty();
        assertThat(beyond.page()).isEqualTo(3);
        assertThat(beyond.totalElements()).isEqualTo(5);

        MetricSamplePage empty = metrics(applianceId, to.plusSeconds(1), to.plusSeconds(2), 4, 3);
        assertThat(empty.items()).isEmpty();
        assertThat(empty.page()).isEqualTo(4);
        assertThat(empty.size()).isEqualTo(3);
        assertThat(empty.totalElements()).isZero();
        assertThat(empty.totalPages()).isZero();
    }

    @Test
    void unknownApplianceQueriesReturnEmptyPagesWithoutExistenceLookup() {
        UUID unknown = UUID.randomUUID();

        CollectionAttemptPage attempts = attempts(
                unknown, Optional.empty(), Optional.empty(), 2, 5);
        MetricSamplePage samples = metrics(unknown, BASE, BASE.plusSeconds(1), 3, 7);

        assertThat(attempts.items()).isEmpty();
        assertThat(attempts.page()).isEqualTo(2);
        assertThat(attempts.size()).isEqualTo(5);
        assertThat(attempts.totalElements()).isZero();
        assertThat(attempts.totalPages()).isZero();
        assertThat(samples.items()).isEmpty();
        assertThat(samples.page()).isEqualTo(3);
        assertThat(samples.size()).isEqualTo(7);
        assertThat(samples.totalElements()).isZero();
        assertThat(samples.totalPages()).isZero();
    }

    private void insertSingleSampleAttempt(
            UUID applianceId,
            UUID attemptId,
            Instant startedAt,
            CollectionTrigger trigger,
            CollectionOutcome outcome) {
        if (outcome == CollectionOutcome.FAILED) {
            metricsRepository.insert(failed(
                    applianceId,
                    attemptId,
                    startedAt,
                    null,
                    List.of(),
                    new CollectionFailure(CollectionFailureCategory.TIMEOUT, null, null),
                    trigger));
            return;
        }
        List<CollectionWarning> warnings = outcome == CollectionOutcome.PARTIAL_SUCCESS
                ? List.of(UNKNOWN_WARNING)
                : List.of();
        MetricSample sample = sample(
                UUID.randomUUID(), applianceId, attemptId, CanonicalMetric.POWER,
                CanonicalUnit.WATT, "1", startedAt, startedAt.plusSeconds(1));
        CollectionAttempt attempt = new CollectionAttempt(
                attemptId,
                applianceId,
                trigger,
                outcome,
                startedAt,
                startedAt.plusSeconds(1),
                1,
                warnings,
                null,
                null);
        metricsRepository.insert(new CompletedCollection(attempt, List.of(sample)));
    }

    private CompletedCollection success(
            UUID applianceId,
            UUID attemptId,
            Instant startedAt,
            Instant dueAt,
            List<MetricSample> samples) {
        return new CompletedCollection(
                new CollectionAttempt(
                        attemptId,
                        applianceId,
                        CollectionTrigger.MANUAL,
                        CollectionOutcome.SUCCESS,
                        startedAt,
                        startedAt.plusSeconds(2),
                        samples.size(),
                        List.of(),
                        null,
                        dueAt),
                samples);
    }

    private CompletedCollection partial(
            UUID applianceId,
            UUID attemptId,
            Instant startedAt,
            Instant dueAt,
            List<CollectionWarning> warnings,
            List<MetricSample> samples) {
        return new CompletedCollection(
                new CollectionAttempt(
                        attemptId,
                        applianceId,
                        CollectionTrigger.MANUAL,
                        CollectionOutcome.PARTIAL_SUCCESS,
                        startedAt,
                        startedAt.plusSeconds(2),
                        samples.size(),
                        warnings,
                        null,
                        dueAt),
                samples);
    }

    private CompletedCollection failed(
            UUID applianceId,
            UUID attemptId,
            Instant startedAt,
            Instant dueAt,
            List<CollectionWarning> warnings,
            CollectionFailure failure) {
        return failed(
                applianceId,
                attemptId,
                startedAt,
                dueAt,
                warnings,
                failure,
                CollectionTrigger.SCHEDULED);
    }

    private CompletedCollection failed(
            UUID applianceId,
            UUID attemptId,
            Instant startedAt,
            Instant dueAt,
            List<CollectionWarning> warnings,
            CollectionFailure failure,
            CollectionTrigger trigger) {
        return new CompletedCollection(
                new CollectionAttempt(
                        attemptId,
                        applianceId,
                        trigger,
                        CollectionOutcome.FAILED,
                        startedAt,
                        startedAt.plusSeconds(2),
                        0,
                        warnings,
                        failure,
                        dueAt),
                List.of());
    }

    private MetricSample sample(
            UUID id,
            UUID applianceId,
            UUID attemptId,
            CanonicalMetric metric,
            CanonicalUnit unit,
            String value,
            Instant observedAt,
            Instant ingestedAt) {
        return new MetricSample(
                id,
                applianceId,
                attemptId,
                metric,
                unit,
                new BigDecimal(value),
                observedAt,
                ingestedAt);
    }

    private CollectionAttemptPage attempts(
            UUID applianceId,
            Optional<CollectionTrigger> trigger,
            Optional<CollectionOutcome> outcome,
            int page,
            int size) {
        return metricsRepository.findAttempts(new CollectionAttemptQuery(
                applianceId, trigger, outcome, new HistoryPageRequest(page, size)));
    }

    private MetricSamplePage metrics(
            UUID applianceId, Instant from, Instant to, int page, int size) {
        return metricsRepository.findMetricSamples(new MetricSampleQuery(
                applianceId, from, to, new HistoryPageRequest(page, size)));
    }

    private UUID insertAppliance(String externalReference) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO appliance (
                    id, display_name, vendor_key, external_reference,
                    collection_interval_seconds, next_collection_due_at,
                    created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                "Metrics fixture",
                "mock-alpha",
                externalReference + "-" + id,
                30,
                jdbcTime(BASE.plusSeconds(30)),
                jdbcTime(BASE),
                jdbcTime(BASE));
        return id;
    }

    private ApplianceSnapshot applianceSnapshot(UUID id) {
        return jdbcTemplate.queryForObject(
                """
                SELECT next_collection_due_at, consecutive_failure_count,
                       last_collection_status, version, updated_at
                FROM appliance WHERE id = ?
                """,
                (resultSet, rowNumber) -> new ApplianceSnapshot(
                        resultSet.getObject("next_collection_due_at", OffsetDateTime.class).toInstant(),
                        resultSet.getInt("consecutive_failure_count"),
                        resultSet.getString("last_collection_status"),
                        resultSet.getLong("version"),
                        resultSet.getObject("updated_at", OffsetDateTime.class).toInstant()),
                id);
    }

    private int count(String table, String idColumn, UUID id) {
        String sql = "SELECT COUNT(*) FROM " + table + " WHERE " + idColumn + " = ?";
        return jdbcTemplate.queryForObject(sql, Integer.class, id);
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString(String.format("00000000-0000-0000-0000-%012d", suffix));
    }

    private static OffsetDateTime jdbcTime(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private record ApplianceSnapshot(
            Instant nextDueAt,
            int failureCount,
            String lastStatus,
            long version,
            Instant updatedAt) {}
}
