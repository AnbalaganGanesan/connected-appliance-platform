package com.example.connectedappliance.metrics.application.collection;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.example.connectedappliance.appliance.application.port.in.ApplianceCollectionCommandPort;
import com.example.connectedappliance.appliance.application.port.in.ApplianceCollectionFinalizationCommand;
import com.example.connectedappliance.appliance.application.port.in.ApplianceCollectionFinalizationState;
import com.example.connectedappliance.appliance.domain.CollectionState;
import com.example.connectedappliance.appliance.domain.LastCollectionStatus;
import com.example.connectedappliance.metrics.application.control.CollectionBackoffPolicy;
import com.example.connectedappliance.metrics.application.control.CollectionSchedulingPolicy;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CollectionFinalizationServiceTest {

    private static final UUID APPLIANCE_ID = new UUID(0, 1);
    private static final UUID ATTEMPT_ID = new UUID(0, 2);
    private static final UUID SAMPLE_ID = new UUID(0, 3);
    private static final Instant STARTED = Instant.parse("2026-07-22T10:00:00Z");
    private static final Instant COMPLETED = Instant.parse("2026-07-22T10:00:01Z");
    private static final Instant OLD_DUE = Instant.parse("2026-07-22T09:59:00Z");

    private ApplianceCollectionCommandPort appliancePort;
    private MetricsRepository metricsRepository;
    private CollectionFinalizationService service;

    @BeforeEach
    void setUp() {
        appliancePort = mock(ApplianceCollectionCommandPort.class);
        metricsRepository = mock(MetricsRepository.class);
        service = new CollectionFinalizationService(
                appliancePort,
                metricsRepository,
                new CollectionSchedulingPolicy(new CollectionBackoffPolicy(Duration.ofHours(1))));
        when(metricsRepository.insert(any())).thenAnswer(invocation ->
                invocation.<CompletedCollection>getArgument(0).attempt());
        when(appliancePort.applyCollectionFinalization(any())).thenAnswer(invocation -> {
            ApplianceCollectionFinalizationCommand command = invocation.getArgument(0);
            CollectionState state = command.nextCollectionDueAt() == null
                    ? CollectionState.PAUSED
                    : CollectionState.ACTIVE;
            return Optional.of(new ApplianceCollectionFinalizationState(
                    APPLIANCE_ID,
                    state,
                    60,
                    command.consecutiveFailureCount(),
                    command.lastCollectionStatus(),
                    command.nextCollectionDueAt()));
        });
    }

    @Test
    void finalizesSuccessUsingLatestActiveIntervalAndResetsFailureCount() {
        lockState(CollectionState.ACTIVE, 60, 7);

        CollectionAttempt result = service.finalizeCollection(success());

        assertThat(result.outcome()).isEqualTo(CollectionOutcome.SUCCESS);
        assertThat(result.nextCollectionDueAt()).isEqualTo(COMPLETED.plusSeconds(60));
        ApplianceCollectionFinalizationCommand command = captureCommand();
        assertThat(command.lastCollectionStatus()).isEqualTo(LastCollectionStatus.SUCCESS);
        assertThat(command.consecutiveFailureCount()).isZero();
        assertThat(command.nextCollectionDueAt()).isEqualTo(result.nextCollectionDueAt());
        assertThat(command.completedAt()).isEqualTo(COMPLETED);
        assertInsertThenUpdateOrder();
    }

    @Test
    void latestPausedStateForcesNullDueWithoutReactivation() {
        lockState(CollectionState.PAUSED, 60, 4);

        CollectionAttempt result = service.finalizeCollection(success());

        assertThat(result.nextCollectionDueAt()).isNull();
        ApplianceCollectionFinalizationCommand command = captureCommand();
        assertThat(command.nextCollectionDueAt()).isNull();
        assertThat(command.consecutiveFailureCount()).isZero();
        assertThat(command.lastCollectionStatus()).isEqualTo(LastCollectionStatus.SUCCESS);
    }

    @Test
    void finalizesPartialSuccessWithOrderedWarningsAndLatestInterval() {
        lockState(CollectionState.ACTIVE, 90, 3);
        List<CollectionWarning> warnings = List.of(
                new CollectionWarning("UNKNOWN_METRIC", "Unsupported reading omitted."),
                new CollectionWarning("MALFORMED_VALUE", "Malformed reading omitted."));

        CollectionAttempt result = service.finalizeCollection(partial(warnings));

        assertThat(result.outcome()).isEqualTo(CollectionOutcome.PARTIAL_SUCCESS);
        assertThat(result.warnings()).containsExactlyElementsOf(warnings);
        assertThat(result.nextCollectionDueAt()).isEqualTo(COMPLETED.plusSeconds(90));
        ApplianceCollectionFinalizationCommand command = captureCommand();
        assertThat(command.lastCollectionStatus()).isEqualTo(LastCollectionStatus.PARTIAL_SUCCESS);
        assertThat(command.consecutiveFailureCount()).isZero();
    }

    @Test
    void failedTimeoutUsesLatestFailureCountAndTaskSeventeenBackoff() {
        lockState(CollectionState.ACTIVE, 10, 2);
        CollectionFailure failure = new CollectionFailure(
                CollectionFailureCategory.TIMEOUT, "The vendor request timed out.", null);

        CollectionAttempt result = service.finalizeCollection(failed(failure, List.of()));

        assertThat(result.outcome()).isEqualTo(CollectionOutcome.FAILED);
        assertThat(result.sampleCount()).isZero();
        assertThat(result.nextCollectionDueAt()).isEqualTo(COMPLETED.plusSeconds(80));
        ApplianceCollectionFinalizationCommand command = captureCommand();
        assertThat(command.lastCollectionStatus()).isEqualTo(LastCollectionStatus.FAILED);
        assertThat(command.consecutiveFailureCount()).isEqualTo(3);
        assertThat(command.nextCollectionDueAt()).isEqualTo(result.nextCollectionDueAt());
    }

    @Test
    void failedRateLimitUsesRetryAfterWhenGreaterThanBackoff() {
        lockState(CollectionState.ACTIVE, 10, 0);
        CollectionFailure failure = new CollectionFailure(
                CollectionFailureCategory.RATE_LIMITED, "Retry later.", 45);
        List<CollectionWarning> warnings = List.of(
                new CollectionWarning("UNKNOWN_METRIC", "Unsupported reading omitted."));

        CollectionAttempt result = service.finalizeCollection(failed(failure, warnings));

        assertThat(result.nextCollectionDueAt()).isEqualTo(COMPLETED.plusSeconds(45));
        assertThat(result.warnings()).containsExactlyElementsOf(warnings);
        assertThat(captureCommand().consecutiveFailureCount()).isEqualTo(1);
    }

    @Test
    void preservesPreparedSampleOrderInAtomicPersistenceInput() {
        lockState(CollectionState.ACTIVE, 30, 0);
        MetricSample temperature = new MetricSample(
                SAMPLE_ID,
                APPLIANCE_ID,
                ATTEMPT_ID,
                CanonicalMetric.TEMPERATURE,
                CanonicalUnit.CELSIUS,
                new BigDecimal("21.5"),
                COMPLETED,
                COMPLETED);
        MetricSample power = new MetricSample(
                new UUID(0, 4),
                APPLIANCE_ID,
                ATTEMPT_ID,
                CanonicalMetric.POWER,
                CanonicalUnit.WATT,
                new BigDecimal("125"),
                COMPLETED,
                COMPLETED);
        PreparedCollectionOutcome prepared = new PreparedCollectionOutcome(
                APPLIANCE_ID,
                CollectionTrigger.MANUAL,
                ATTEMPT_ID,
                STARTED,
                COMPLETED,
                CollectionOutcome.SUCCESS,
                List.of(),
                null,
                List.of(temperature, power));

        service.finalizeCollection(prepared);

        ArgumentCaptor<CompletedCollection> captor =
                ArgumentCaptor.forClass(CompletedCollection.class);
        verify(metricsRepository).insert(captor.capture());
        assertThat(captor.getValue().samples()).containsExactly(temperature, power);
        assertThat(captor.getValue().attempt().sampleCount()).isEqualTo(2);
    }

    @Test
    void missingLockedApplianceSignalsSanitizedFailureBeforeMetricsInsertion() {
        when(appliancePort.lockForCollectionFinalization(APPLIANCE_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.finalizeCollection(success()))
                .isInstanceOf(CollectionFinalizationException.class)
                .hasMessage("Collection finalization could not be completed.");

        verify(metricsRepository, never()).insert(any());
        verify(appliancePort, never()).applyCollectionFinalization(any());
    }

    private void lockState(CollectionState state, int interval, int failureCount) {
        when(appliancePort.lockForCollectionFinalization(APPLIANCE_ID)).thenReturn(Optional.of(
                new ApplianceCollectionFinalizationState(
                        APPLIANCE_ID,
                        state,
                        interval,
                        failureCount,
                        LastCollectionStatus.NEVER_ATTEMPTED,
                        state == CollectionState.ACTIVE ? OLD_DUE : null)));
    }

    private PreparedCollectionOutcome success() {
        return prepared(CollectionOutcome.SUCCESS, List.of(), null);
    }

    private PreparedCollectionOutcome partial(List<CollectionWarning> warnings) {
        return prepared(CollectionOutcome.PARTIAL_SUCCESS, warnings, null);
    }

    private PreparedCollectionOutcome failed(
            CollectionFailure failure, List<CollectionWarning> warnings) {
        return new PreparedCollectionOutcome(
                APPLIANCE_ID,
                CollectionTrigger.MANUAL,
                ATTEMPT_ID,
                STARTED,
                COMPLETED,
                CollectionOutcome.FAILED,
                warnings,
                failure,
                List.of());
    }

    private PreparedCollectionOutcome prepared(
            CollectionOutcome outcome,
            List<CollectionWarning> warnings,
            CollectionFailure failure) {
        MetricSample sample = new MetricSample(
                SAMPLE_ID,
                APPLIANCE_ID,
                ATTEMPT_ID,
                CanonicalMetric.POWER,
                CanonicalUnit.WATT,
                new BigDecimal("125"),
                COMPLETED,
                COMPLETED);
        return new PreparedCollectionOutcome(
                APPLIANCE_ID,
                CollectionTrigger.MANUAL,
                ATTEMPT_ID,
                STARTED,
                COMPLETED,
                outcome,
                warnings,
                failure,
                List.of(sample));
    }

    private ApplianceCollectionFinalizationCommand captureCommand() {
        ArgumentCaptor<ApplianceCollectionFinalizationCommand> captor =
                ArgumentCaptor.forClass(ApplianceCollectionFinalizationCommand.class);
        verify(appliancePort).applyCollectionFinalization(captor.capture());
        return captor.getValue();
    }

    private void assertInsertThenUpdateOrder() {
        InOrder order = inOrder(metricsRepository, appliancePort);
        order.verify(appliancePort).lockForCollectionFinalization(APPLIANCE_ID);
        order.verify(metricsRepository).insert(any());
        order.verify(appliancePort).applyCollectionFinalization(any());
    }
}
