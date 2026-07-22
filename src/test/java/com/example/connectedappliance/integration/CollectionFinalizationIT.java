package com.example.connectedappliance.integration;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.example.connectedappliance.appliance.application.ApplianceCollectionConfigurationService;
import com.example.connectedappliance.appliance.application.UpdateCollectionIntervalCommand;
import com.example.connectedappliance.appliance.application.UpdateCollectionStateCommand;
import com.example.connectedappliance.appliance.application.port.out.ApplianceRepository;
import com.example.connectedappliance.appliance.domain.Appliance;
import com.example.connectedappliance.appliance.domain.CollectionState;
import com.example.connectedappliance.appliance.domain.LastCollectionStatus;
import com.example.connectedappliance.metrics.application.collection.CollectApplianceCommand;
import com.example.connectedappliance.metrics.application.collection.CollectionFinalizationService;
import com.example.connectedappliance.metrics.application.collection.CollectionWorkflowResult;
import com.example.connectedappliance.metrics.application.collection.MetricCollectionService;
import com.example.connectedappliance.metrics.application.collection.PreparedCollectionOutcome;
import com.example.connectedappliance.metrics.application.port.out.CollectionAttemptPage;
import com.example.connectedappliance.metrics.application.port.out.CollectionAttemptQuery;
import com.example.connectedappliance.metrics.application.port.out.HistoryPageRequest;
import com.example.connectedappliance.metrics.application.port.out.MetricsIdentifierGenerator;
import com.example.connectedappliance.metrics.application.port.out.MetricsRepository;
import com.example.connectedappliance.metrics.application.port.out.VendorFailureCategory;
import com.example.connectedappliance.metrics.application.port.out.VendorMetricBatch;
import com.example.connectedappliance.metrics.application.port.out.VendorMetricException;
import com.example.connectedappliance.metrics.application.port.out.VendorMetricRequest;
import com.example.connectedappliance.metrics.application.port.out.VendorMetricSourcePort;
import com.example.connectedappliance.metrics.application.port.out.VendorMetricWarning;
import com.example.connectedappliance.metrics.application.port.out.VendorMetricWarningCode;
import com.example.connectedappliance.metrics.domain.CollectionAttempt;
import com.example.connectedappliance.metrics.domain.CollectionFailure;
import com.example.connectedappliance.metrics.domain.CollectionFailureCategory;
import com.example.connectedappliance.metrics.domain.CollectionOutcome;
import com.example.connectedappliance.metrics.domain.CollectionTrigger;
import com.example.connectedappliance.metrics.domain.MetricSample;
import com.example.connectedappliance.shared.metric.CanonicalMetric;
import com.example.connectedappliance.shared.metric.CanonicalMetricReading;
import com.example.connectedappliance.shared.metric.CanonicalUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import({Task10FixedClockConfiguration.class, CollectionFinalizationIT.Task18TestConfiguration.class})
@Execution(ExecutionMode.SAME_THREAD)
class CollectionFinalizationIT extends PostgresIntegrationTestSupport {

    private static final Instant CREATED = Instant.parse("2026-07-22T09:00:00Z");
    private static final Instant STARTED = Instant.parse("2026-07-22T10:00:00Z");
    private static final Instant COMPLETED = Instant.parse("2026-07-22T10:00:01Z");

    @Autowired
    private MetricCollectionService collectionService;

    @Autowired
    private CollectionFinalizationService finalizationService;

    @Autowired
    private ApplianceCollectionConfigurationService configurationService;

    @Autowired
    private ApplianceRepository applianceRepository;

    @Autowired
    private MetricsRepository metricsRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Task10FixedClockConfiguration.MutableUtcClock clock;

    @Autowired
    private ControlledVendorMetricSource vendor;

    @Autowired
    private DeterministicMetricsIdentifierGenerator identifiers;

    @BeforeEach
    @AfterEach
    void cleanDatabaseAndResetControls() {
        jdbcTemplate.update("DELETE FROM metric_sample");
        jdbcTemplate.update("DELETE FROM collection_warning");
        jdbcTemplate.update("DELETE FROM collection_attempt");
        jdbcTemplate.update("DELETE FROM appliance");
        clock.set(STARTED);
        vendor.reset();
        identifiers.reset();
    }

    @Test
    void successPersistsAttemptSamplesAndLatestApplianceStateAtomically() {
        UUID applianceId = insertActiveAppliance("success", 30, 4);
        UUID attemptId = uuid(101);
        identifiers.reset(attemptId, uuid(102), uuid(103));

        CollectionAttempt attempt = persisted(collectionService.collect(
                new CollectApplianceCommand(applianceId, CollectionTrigger.MANUAL)));

        Appliance updated = applianceRepository.findById(applianceId).orElseThrow();
        assertThat(attempt.outcome()).isEqualTo(CollectionOutcome.SUCCESS);
        assertThat(attempt.startedAt()).isEqualTo(STARTED);
        assertThat(attempt.completedAt()).isEqualTo(COMPLETED);
        assertThat(attempt.sampleCount()).isEqualTo(2);
        assertThat(attempt.nextCollectionDueAt()).isEqualTo(COMPLETED.plusSeconds(30));
        assertThat(updated.lastCollectionStatus()).isEqualTo(LastCollectionStatus.SUCCESS);
        assertThat(updated.consecutiveFailureCount()).isZero();
        assertThat(updated.nextCollectionDueAt()).isEqualTo(attempt.nextCollectionDueAt());
        assertThat(updated.updatedAt()).isEqualTo(COMPLETED);
        assertThat(updated.version()).isEqualTo(1);
        assertThat(count("collection_attempt", attemptId)).isEqualTo(1);
        assertThat(countByAttempt("metric_sample", attemptId)).isEqualTo(2);
        assertThat(countByAttempt("collection_warning", attemptId)).isZero();
        assertThat(jdbcTemplate.queryForList(
                        "SELECT observed_at = ingested_at FROM metric_sample WHERE collection_attempt_id = ?",
                        Boolean.class,
                        attemptId))
                .containsOnly(true);
        assertThat(vendor.transactionActiveDuringCall()).isFalse();
        assertThat(vendor.lastRequest().externalReference()).isEqualTo("  Opaque-success  ");
    }

    @Test
    void partialSuccessPersistsOrderedWarningsAndValidSamplesWithoutBackoff() {
        UUID applianceId = insertActiveAppliance("partial", 45, 3);
        UUID attemptId = uuid(111);
        identifiers.reset(attemptId, uuid(112));
        vendor.mode(Mode.PARTIAL);

        CollectionAttempt attempt = persisted(collectionService.collect(
                new CollectApplianceCommand(applianceId, CollectionTrigger.SCHEDULED)));

        Appliance updated = applianceRepository.findById(applianceId).orElseThrow();
        assertThat(attempt.outcome()).isEqualTo(CollectionOutcome.PARTIAL_SUCCESS);
        assertThat(attempt.warnings()).extracting(warning -> warning.code())
                .containsExactly("UNKNOWN_METRIC", "MALFORMED_VALUE");
        assertThat(attempt.sampleCount()).isEqualTo(1);
        assertThat(attempt.nextCollectionDueAt()).isEqualTo(COMPLETED.plusSeconds(45));
        assertThat(updated.lastCollectionStatus()).isEqualTo(LastCollectionStatus.PARTIAL_SUCCESS);
        assertThat(updated.consecutiveFailureCount()).isZero();
        assertThat(jdbcTemplate.queryForList(
                        """
                        SELECT warning_index FROM collection_warning
                        WHERE collection_attempt_id = ? ORDER BY warning_index
                        """,
                        Integer.class,
                        attemptId))
                .containsExactly(0, 1);
    }

    @Test
    void failedTimeoutPersistsFailureAndAppliesBackoffFromLatestCount() {
        assertFailedFinalization(
                "timeout",
                Mode.TIMEOUT,
                CollectionFailureCategory.TIMEOUT,
                null,
                60);
    }

    @Test
    void failedRateLimitPersistsRetryAfterAndUsesItsLongerDelay() {
        assertFailedFinalization(
                "rate",
                Mode.RATE_LIMITED,
                CollectionFailureCategory.RATE_LIMITED,
                90,
                90);
    }

    @Test
    void failedInvalidDataPersistsOrderedWarningsWithoutSamples() {
        UUID applianceId = insertActiveAppliance("invalid", 30, 0);
        UUID attemptId = uuid(121);
        identifiers.reset(attemptId);
        vendor.mode(Mode.INVALID_DATA);

        CollectionAttempt attempt = persisted(collectionService.collect(
                new CollectApplianceCommand(applianceId, CollectionTrigger.MANUAL)));

        assertThat(attempt.outcome()).isEqualTo(CollectionOutcome.FAILED);
        assertThat(attempt.failure().category()).isEqualTo(CollectionFailureCategory.INVALID_DATA);
        assertThat(attempt.warnings()).extracting(warning -> warning.code())
                .containsExactly("UNKNOWN_METRIC", "MALFORMED_VALUE");
        assertThat(attempt.sampleCount()).isZero();
        assertThat(countByAttempt("metric_sample", attemptId)).isZero();
    }

    @Test
    void pauseDuringVendorCallIsPreservedAndFinalizationKeepsBothDueValuesNull()
            throws Exception {
        UUID applianceId = insertActiveAppliance("pause-concurrent", 30, 0);
        UUID attemptId = uuid(131);
        identifiers.reset(attemptId, uuid(132), uuid(133));
        vendor.blockNextCall();

        CollectionAttempt attempt = collectWhileChangingConfiguration(
                applianceId,
                () -> configurationService.updateCollectionState(
                        new UpdateCollectionStateCommand(applianceId, CollectionState.PAUSED)));

        Appliance updated = applianceRepository.findById(applianceId).orElseThrow();
        assertThat(updated.collectionState()).isEqualTo(CollectionState.PAUSED);
        assertThat(updated.nextCollectionDueAt()).isNull();
        assertThat(updated.collectionIntervalSeconds()).isEqualTo(30);
        assertThat(updated.lastCollectionStatus()).isEqualTo(LastCollectionStatus.SUCCESS);
        assertThat(attempt.nextCollectionDueAt()).isNull();
        assertThat(updated.version()).isEqualTo(2);
    }

    @Test
    void intervalChangeDuringVendorCallControlsFinalDueWithoutChangingState()
            throws Exception {
        UUID applianceId = insertActiveAppliance("interval-concurrent", 30, 0);
        UUID attemptId = uuid(141);
        identifiers.reset(attemptId, uuid(142), uuid(143));
        vendor.blockNextCall();

        CollectionAttempt attempt = collectWhileChangingConfiguration(
                applianceId,
                () -> configurationService.updateCollectionInterval(
                        new UpdateCollectionIntervalCommand(applianceId, 120)));

        Appliance updated = applianceRepository.findById(applianceId).orElseThrow();
        assertThat(updated.collectionState()).isEqualTo(CollectionState.ACTIVE);
        assertThat(updated.collectionIntervalSeconds()).isEqualTo(120);
        assertThat(updated.nextCollectionDueAt()).isEqualTo(COMPLETED.plusSeconds(120));
        assertThat(attempt.nextCollectionDueAt()).isEqualTo(updated.nextCollectionDueAt());
        assertThat(updated.version()).isEqualTo(2);
    }

    @Test
    void metricsInsertionFailureRollsBackApplianceLatestSummary() {
        UUID applianceId = insertActiveAppliance("metrics-rollback", 30, 2);
        UUID duplicateAttemptId = uuid(151);
        identifiers.reset(duplicateAttemptId, uuid(152), uuid(153));
        persisted(collectionService.collect(
                new CollectApplianceCommand(applianceId, CollectionTrigger.MANUAL)));
        Appliance before = applianceRepository.findById(applianceId).orElseThrow();

        clock.set(STARTED.plusSeconds(10));
        vendor.completionAt(COMPLETED.plusSeconds(10));
        identifiers.reset(duplicateAttemptId, uuid(154), uuid(155));

        assertThatThrownBy(() -> collectionService.collect(
                        new CollectApplianceCommand(applianceId, CollectionTrigger.MANUAL)))
                .isInstanceOf(DataIntegrityViolationException.class);

        Appliance after = applianceRepository.findById(applianceId).orElseThrow();
        assertThat(after.consecutiveFailureCount()).isEqualTo(before.consecutiveFailureCount());
        assertThat(after.lastCollectionStatus()).isEqualTo(before.lastCollectionStatus());
        assertThat(after.nextCollectionDueAt()).isEqualTo(before.nextCollectionDueAt());
        assertThat(after.updatedAt()).isEqualTo(before.updatedAt());
        assertThat(after.version()).isEqualTo(before.version());
        assertThat(count("collection_attempt", duplicateAttemptId)).isEqualTo(1);
    }

    @Test
    void applianceDomainFailureAfterMetricsInsertRollsBackAttemptSamplesAndAppliance() {
        UUID applianceId = insertActiveAppliance("appliance-rollback", 30, 1);
        Appliance before = applianceRepository.findById(applianceId).orElseThrow();
        UUID attemptId = uuid(161);
        Instant invalidStart = CREATED.minusSeconds(20);
        Instant invalidCompletion = CREATED.minusSeconds(10);
        MetricSample sample = new MetricSample(
                uuid(162),
                applianceId,
                attemptId,
                CanonicalMetric.POWER,
                CanonicalUnit.WATT,
                new BigDecimal("125"),
                invalidCompletion,
                invalidCompletion);
        PreparedCollectionOutcome prepared = new PreparedCollectionOutcome(
                applianceId,
                CollectionTrigger.MANUAL,
                attemptId,
                invalidStart,
                invalidCompletion,
                CollectionOutcome.SUCCESS,
                List.of(),
                null,
                List.of(sample));

        assertThatThrownBy(() -> finalizationService.finalizeCollection(prepared))
                .isInstanceOf(RuntimeException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class);

        Appliance after = applianceRepository.findById(applianceId).orElseThrow();
        assertThat(after.consecutiveFailureCount()).isEqualTo(before.consecutiveFailureCount());
        assertThat(after.lastCollectionStatus()).isEqualTo(before.lastCollectionStatus());
        assertThat(after.nextCollectionDueAt()).isEqualTo(before.nextCollectionDueAt());
        assertThat(after.updatedAt()).isEqualTo(before.updatedAt());
        assertThat(after.version()).isEqualTo(before.version());
        assertThat(count("collection_attempt", attemptId)).isZero();
        assertThat(countByAttempt("metric_sample", attemptId)).isZero();
    }

    private void assertFailedFinalization(
            String reference,
            Mode mode,
            CollectionFailureCategory expectedCategory,
            Integer expectedRetryAfter,
            int expectedDelaySeconds) {
        UUID applianceId = insertActiveAppliance(reference, 30, 0);
        UUID attemptId = UUID.randomUUID();
        identifiers.reset(attemptId);
        vendor.mode(mode);

        CollectionAttempt attempt = persisted(collectionService.collect(
                new CollectApplianceCommand(applianceId, CollectionTrigger.MANUAL)));

        Appliance updated = applianceRepository.findById(applianceId).orElseThrow();
        assertThat(attempt.outcome()).isEqualTo(CollectionOutcome.FAILED);
        assertThat(attempt.sampleCount()).isZero();
        assertThat(attempt.failure().category()).isEqualTo(expectedCategory);
        assertThat(attempt.failure().retryAfterSeconds()).isEqualTo(expectedRetryAfter);
        assertThat(attempt.nextCollectionDueAt())
                .isEqualTo(COMPLETED.plusSeconds(expectedDelaySeconds));
        assertThat(updated.lastCollectionStatus()).isEqualTo(LastCollectionStatus.FAILED);
        assertThat(updated.consecutiveFailureCount()).isEqualTo(1);
        assertThat(updated.nextCollectionDueAt()).isEqualTo(attempt.nextCollectionDueAt());
        assertThat(countByAttempt("metric_sample", attemptId)).isZero();
    }

    private CollectionAttempt collectWhileChangingConfiguration(
            UUID applianceId, Runnable configurationChange) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<CollectionWorkflowResult> future = executor.submit(() -> collectionService.collect(
                    new CollectApplianceCommand(applianceId, CollectionTrigger.MANUAL)));
            vendor.awaitCallEntered();
            clock.set(STARTED.plusMillis(500));
            configurationChange.run();
            clock.set(COMPLETED);
            vendor.releaseCall();
            return persisted(future.get(5, TimeUnit.SECONDS));
        } finally {
            vendor.releaseCall();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private UUID insertActiveAppliance(String reference, int interval, int failures) {
        UUID id = UUID.randomUUID();
        applianceRepository.insert(new Appliance(
                id,
                "Task 18 appliance",
                "Integration test",
                "mock-alpha",
                "  Opaque-" + reference + "  ",
                CollectionState.ACTIVE,
                interval,
                STARTED.minusSeconds(1),
                failures,
                failures == 0 ? LastCollectionStatus.NEVER_ATTEMPTED : LastCollectionStatus.FAILED,
                0,
                CREATED,
                CREATED));
        return id;
    }

    private CollectionAttempt persisted(CollectionWorkflowResult result) {
        assertThat(result).isInstanceOf(CollectionWorkflowResult.Persisted.class);
        return ((CollectionWorkflowResult.Persisted) result).attempt();
    }

    private int count(String table, UUID id) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM " + table + " WHERE id = ?", Integer.class, id);
    }

    private int countByAttempt(String table, UUID attemptId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM " + table + " WHERE collection_attempt_id = ?",
                Integer.class,
                attemptId);
    }

    private static UUID uuid(long value) {
        return new UUID(0, value);
    }

    enum Mode {
        SUCCESS,
        PARTIAL,
        TIMEOUT,
        RATE_LIMITED,
        INVALID_DATA,
        BLOCK_SUCCESS
    }

    static final class ControlledVendorMetricSource implements VendorMetricSourcePort {

        private final Task10FixedClockConfiguration.MutableUtcClock clock;
        private final AtomicReference<Mode> mode = new AtomicReference<>(Mode.SUCCESS);
        private final AtomicReference<Instant> completionAt = new AtomicReference<>(COMPLETED);
        private final AtomicReference<VendorMetricRequest> lastRequest = new AtomicReference<>();
        private final AtomicBoolean transactionActive = new AtomicBoolean();
        private volatile CountDownLatch entered = new CountDownLatch(0);
        private volatile CountDownLatch release = new CountDownLatch(0);

        ControlledVendorMetricSource(Task10FixedClockConfiguration.MutableUtcClock clock) {
            this.clock = clock;
        }

        @Override
        public VendorMetricBatch collect(VendorMetricRequest request) {
            lastRequest.set(request);
            transactionActive.set(TransactionSynchronizationManager.isActualTransactionActive());
            Mode selected = mode.get();
            if (selected == Mode.BLOCK_SUCCESS) {
                entered.countDown();
                await(release);
            } else {
                clock.set(completionAt.get());
            }
            return switch (selected) {
                case SUCCESS, BLOCK_SUCCESS -> successBatch();
                case PARTIAL -> partialBatch();
                case TIMEOUT -> throw failure(VendorFailureCategory.TIMEOUT, null, List.of());
                case RATE_LIMITED -> throw failure(VendorFailureCategory.RATE_LIMITED, 90, List.of());
                case INVALID_DATA -> throw failure(
                        VendorFailureCategory.INVALID_DATA,
                        null,
                        warningBatch());
            };
        }

        void reset() {
            mode.set(Mode.SUCCESS);
            completionAt.set(COMPLETED);
            lastRequest.set(null);
            transactionActive.set(false);
            entered = new CountDownLatch(0);
            release = new CountDownLatch(0);
        }

        void mode(Mode selected) {
            mode.set(selected);
        }

        void completionAt(Instant instant) {
            completionAt.set(instant);
        }

        void blockNextCall() {
            entered = new CountDownLatch(1);
            release = new CountDownLatch(1);
            mode.set(Mode.BLOCK_SUCCESS);
        }

        void awaitCallEntered() {
            await(entered);
        }

        void releaseCall() {
            release.countDown();
        }

        boolean transactionActiveDuringCall() {
            return transactionActive.get();
        }

        VendorMetricRequest lastRequest() {
            return lastRequest.get();
        }

        private VendorMetricBatch successBatch() {
            return new VendorMetricBatch(List.of(
                    new CanonicalMetricReading(
                            CanonicalMetric.TEMPERATURE,
                            CanonicalUnit.CELSIUS,
                            new BigDecimal("21.5")),
                    new CanonicalMetricReading(
                            CanonicalMetric.POWER,
                            CanonicalUnit.WATT,
                            new BigDecimal("125"))));
        }

        private VendorMetricBatch partialBatch() {
            return new VendorMetricBatch(
                    List.of(new CanonicalMetricReading(
                            CanonicalMetric.POWER,
                            CanonicalUnit.WATT,
                            new BigDecimal("125"))),
                    warningBatch());
        }

        private List<VendorMetricWarning> warningBatch() {
            return List.of(
                    VendorMetricWarning.forCode(VendorMetricWarningCode.UNKNOWN_METRIC),
                    VendorMetricWarning.forCode(VendorMetricWarningCode.MALFORMED_VALUE));
        }

        private VendorMetricException failure(
                VendorFailureCategory category,
                Integer retryAfter,
                List<VendorMetricWarning> warnings) {
            return new VendorMetricException(
                    category,
                    category == VendorFailureCategory.TIMEOUT
                            ? "The vendor request timed out."
                            : "The vendor response could not be used.",
                    retryAfter,
                    warnings);
        }

        private void await(CountDownLatch latch) {
            try {
                if (!latch.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Controlled vendor latch timed out");
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Controlled vendor latch interrupted");
            }
        }
    }

    static final class DeterministicMetricsIdentifierGenerator
            implements MetricsIdentifierGenerator {

        private final ConcurrentLinkedQueue<UUID> identifiers = new ConcurrentLinkedQueue<>();

        @Override
        public UUID nextIdentifier() {
            UUID identifier = identifiers.poll();
            if (identifier == null) {
                throw new IllegalStateException("No deterministic Metrics identifier configured");
            }
            return identifier;
        }

        void reset(UUID... values) {
            identifiers.clear();
            identifiers.addAll(Arrays.asList(values));
        }
    }

    static class Task18TestConfiguration {

        @Bean
        @Primary
        ControlledVendorMetricSource controlledVendorMetricSource(
                Task10FixedClockConfiguration.MutableUtcClock clock) {
            return new ControlledVendorMetricSource(clock);
        }

        @Bean
        @Primary
        DeterministicMetricsIdentifierGenerator deterministicMetricsIdentifierGenerator() {
            return new DeterministicMetricsIdentifierGenerator();
        }
    }
}
