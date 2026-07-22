package com.example.connectedappliance.metrics.application.collection;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;

import com.example.connectedappliance.appliance.application.port.in.ApplianceCollectionQueryPort;
import com.example.connectedappliance.appliance.application.port.in.ApplianceCollectionTarget;
import com.example.connectedappliance.appliance.domain.CollectionState;
import com.example.connectedappliance.metrics.application.control.ClassifiedVendorFailure;
import com.example.connectedappliance.metrics.application.control.GuardedVendorExecution;
import com.example.connectedappliance.metrics.application.control.VendorExecutionResult;
import com.example.connectedappliance.metrics.application.port.out.MetricsIdentifierGenerator;
import com.example.connectedappliance.metrics.application.port.out.VendorMetricBatch;
import com.example.connectedappliance.metrics.application.port.out.VendorMetricRequest;
import com.example.connectedappliance.metrics.application.port.out.VendorMetricSourcePort;
import com.example.connectedappliance.metrics.application.port.out.VendorMetricWarning;
import com.example.connectedappliance.metrics.application.port.out.VendorMetricWarningCode;
import com.example.connectedappliance.metrics.domain.CollectionAttempt;
import com.example.connectedappliance.metrics.domain.CollectionFailure;
import com.example.connectedappliance.metrics.domain.CollectionFailureCategory;
import com.example.connectedappliance.metrics.domain.CollectionOutcome;
import com.example.connectedappliance.metrics.domain.CollectionTrigger;
import com.example.connectedappliance.metrics.domain.CollectionWarning;
import com.example.connectedappliance.shared.metric.CanonicalMetric;
import com.example.connectedappliance.shared.metric.CanonicalMetricReading;
import com.example.connectedappliance.shared.metric.CanonicalUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MetricCollectionServiceTest {

    private static final UUID APPLIANCE_ID = uuid(1);
    private static final UUID ATTEMPT_ID = uuid(2);
    private static final UUID SAMPLE_ID_1 = uuid(3);
    private static final UUID SAMPLE_ID_2 = uuid(4);
    private static final Instant STARTED = Instant.parse("2026-07-22T10:00:00Z");
    private static final Instant COMPLETED = Instant.parse("2026-07-22T10:00:01Z");

    private ApplianceCollectionQueryPort queryPort;
    private VendorMetricSourcePort vendorPort;
    private GuardedVendorExecution guardedExecution;
    private CollectionFinalizationService finalizer;
    private Clock clock;
    private MetricsIdentifierGenerator identifiers;
    private MetricCollectionService service;

    @BeforeEach
    void setUp() {
        queryPort = mock(ApplianceCollectionQueryPort.class);
        vendorPort = mock(VendorMetricSourcePort.class);
        guardedExecution = mock(GuardedVendorExecution.class);
        finalizer = mock(CollectionFinalizationService.class);
        clock = mock(Clock.class);
        identifiers = mock(MetricsIdentifierGenerator.class);
        service = new MetricCollectionService(
                queryPort, vendorPort, guardedExecution, finalizer, clock, identifiers);
    }

    @Test
    void returnsNotFoundWithoutExecutionFinalizationOrIdentifiers() {
        when(queryPort.findCollectionTarget(APPLIANCE_ID)).thenReturn(Optional.empty());

        assertThat(service.collect(command())).isInstanceOf(CollectionWorkflowResult.NotFound.class);

        verifyNoInteractions(vendorPort, guardedExecution, finalizer, clock, identifiers);
    }

    @Test
    void returnsPausedWithoutExecutionFinalizationOrIdentifiers() {
        when(queryPort.findCollectionTarget(APPLIANCE_ID)).thenReturn(Optional.of(target(
                CollectionState.PAUSED, "mock-alpha", " Opaque/Aa ")));

        assertThat(service.collect(command())).isInstanceOf(CollectionWorkflowResult.Paused.class);

        verifyNoInteractions(vendorPort, guardedExecution, finalizer, clock, identifiers);
    }

    @Test
    void preparesSuccessWithExactVendorTargetTimestampsAndAssignedSampleIds() throws Exception {
        prepareActiveTarget("Mock-Key", " Opaque/Aa ");
        VendorMetricBatch batch = new VendorMetricBatch(List.of(
                reading(CanonicalMetric.TEMPERATURE, CanonicalUnit.CELSIUS, "21.5"),
                reading(CanonicalMetric.POWER, CanonicalUnit.WATT, "125")));
        when(vendorPort.collect(any())).thenReturn(batch);
        executeActualVendorCallable();
        sequenceClockAndIdentifiers(ATTEMPT_ID, SAMPLE_ID_1, SAMPLE_ID_2);
        CollectionAttempt persisted = persistedAttempt(CollectionOutcome.SUCCESS, 2, List.of(), null);
        when(finalizer.finalizeCollection(any())).thenReturn(persisted);

        CollectionWorkflowResult result = service.collect(command());

        assertThat(result).isEqualTo(new CollectionWorkflowResult.Persisted(persisted));
        ArgumentCaptor<VendorMetricRequest> request = ArgumentCaptor.forClass(VendorMetricRequest.class);
        verify(vendorPort).collect(request.capture());
        assertThat(request.getValue().vendorKey()).isEqualTo("Mock-Key");
        assertThat(request.getValue().externalReference()).isEqualTo(" Opaque/Aa ");
        PreparedCollectionOutcome prepared = capturePrepared();
        assertThat(prepared.outcome()).isEqualTo(CollectionOutcome.SUCCESS);
        assertThat(prepared.startedAt()).isEqualTo(STARTED);
        assertThat(prepared.completedAt()).isEqualTo(COMPLETED);
        assertThat(prepared.attemptId()).isEqualTo(ATTEMPT_ID);
        assertThat(prepared.warnings()).isEmpty();
        assertThat(prepared.failure()).isNull();
        assertThat(prepared.samples()).extracting(sample -> sample.id())
                .containsExactly(SAMPLE_ID_1, SAMPLE_ID_2);
        assertThat(prepared.samples()).allSatisfy(sample -> {
            assertThat(sample.observedAt()).isEqualTo(COMPLETED);
            assertThat(sample.ingestedAt()).isEqualTo(COMPLETED);
            assertThat(sample.value().scale()).isEqualTo(6);
        });
    }

    @Test
    void preparesPartialSuccessAndPreservesWarningAndSampleOrder() throws Exception {
        prepareActiveTarget("mock-beta", "Device-1");
        List<VendorMetricWarning> warnings = List.of(
                VendorMetricWarning.forCode(VendorMetricWarningCode.UNKNOWN_METRIC),
                VendorMetricWarning.forCode(VendorMetricWarningCode.MALFORMED_VALUE));
        when(vendorPort.collect(any())).thenReturn(new VendorMetricBatch(
                List.of(reading(CanonicalMetric.POWER, CanonicalUnit.WATT, "10")), warnings));
        executeActualVendorCallable();
        sequenceClockAndIdentifiers(ATTEMPT_ID, SAMPLE_ID_1);
        when(finalizer.finalizeCollection(any())).thenReturn(persistedAttempt(
                CollectionOutcome.PARTIAL_SUCCESS,
                1,
                List.of(
                        warning(VendorMetricWarningCode.UNKNOWN_METRIC),
                        warning(VendorMetricWarningCode.MALFORMED_VALUE)),
                null));

        service.collect(command());

        PreparedCollectionOutcome prepared = capturePrepared();
        assertThat(prepared.outcome()).isEqualTo(CollectionOutcome.PARTIAL_SUCCESS);
        assertThat(prepared.warnings()).extracting(CollectionWarning::code)
                .containsExactly("UNKNOWN_METRIC", "MALFORMED_VALUE");
        assertThat(prepared.samples()).hasSize(1);
        assertThat(prepared.failure()).isNull();
    }

    @Test
    void preparesFailedAttemptFromClassifiedFailureWithoutSamples() {
        prepareActiveTarget("mock-beta", "Device-1");
        CollectionFailure failure = new CollectionFailure(
                CollectionFailureCategory.RATE_LIMITED, "Retry later.", 45);
        List<CollectionWarning> warnings = List.of(
                new CollectionWarning("UNKNOWN_METRIC", "Unsupported reading omitted."),
                new CollectionWarning("MALFORMED_VALUE", "Malformed reading omitted."));
        when(guardedExecution.execute(eq(APPLIANCE_ID), any()))
                .thenReturn(new VendorExecutionResult.Failed<>(
                        new ClassifiedVendorFailure(failure, warnings)));
        sequenceClockAndIdentifiers(ATTEMPT_ID);
        when(finalizer.finalizeCollection(any())).thenReturn(
                persistedAttempt(CollectionOutcome.FAILED, 0, warnings, failure));

        service.collect(command());

        PreparedCollectionOutcome prepared = capturePrepared();
        assertThat(prepared.outcome()).isEqualTo(CollectionOutcome.FAILED);
        assertThat(prepared.failure()).isEqualTo(failure);
        assertThat(prepared.warnings()).containsExactlyElementsOf(warnings);
        assertThat(prepared.samples()).isEmpty();
        verify(vendorPort, never()).collect(any());
        verify(identifiers).nextIdentifier();
    }

    @Test
    void defensiveEmptyCompletedBatchBecomesSanitizedFailedAttempt() throws Exception {
        prepareActiveTarget("mock-alpha", "Device-1");
        when(vendorPort.collect(any())).thenReturn(new VendorMetricBatch(List.of()));
        executeActualVendorCallable();
        sequenceClockAndIdentifiers(ATTEMPT_ID);
        when(finalizer.finalizeCollection(any())).thenReturn(persistedAttempt(
                CollectionOutcome.FAILED,
                0,
                List.of(),
                new CollectionFailure(
                        CollectionFailureCategory.UNEXPECTED,
                        "The vendor request failed unexpectedly.",
                        null)));

        service.collect(command());

        PreparedCollectionOutcome prepared = capturePrepared();
        assertThat(prepared.outcome()).isEqualTo(CollectionOutcome.FAILED);
        assertThat(prepared.failure().category()).isEqualTo(CollectionFailureCategory.UNEXPECTED);
        assertThat(prepared.failure().message())
                .isEqualTo("The vendor request failed unexpectedly.");
        assertThat(prepared.samples()).isEmpty();
    }

    @Test
    void returnsBusyWithoutFinalizationOrIdentifierGeneration() {
        prepareActiveTarget("mock-alpha", "Device-1");
        when(clock.instant()).thenReturn(STARTED);
        when(guardedExecution.execute(eq(APPLIANCE_ID), any()))
                .thenReturn(new VendorExecutionResult.Busy<>());

        assertThat(service.collect(command())).isInstanceOf(CollectionWorkflowResult.Busy.class);

        verifyNoInteractions(vendorPort, finalizer, identifiers);
        verify(clock).instant();
    }

    @Test
    void returnsSaturatedWithoutFinalizationSchedulingOrIdentifierGeneration() {
        prepareActiveTarget("mock-alpha", "Device-1");
        when(clock.instant()).thenReturn(STARTED);
        when(guardedExecution.execute(eq(APPLIANCE_ID), any()))
                .thenReturn(new VendorExecutionResult.Saturated<>());

        assertThat(service.collect(command()))
                .isInstanceOf(CollectionWorkflowResult.Saturated.class);

        verifyNoInteractions(vendorPort, finalizer, identifiers);
        verify(clock).instant();
    }

    @Test
    void orchestrationMethodIsNotTransactionalWhileFinalizerOwnsTransaction() throws Exception {
        Method orchestration = MetricCollectionService.class.getMethod(
                "collect", CollectApplianceCommand.class);
        Method finalization = CollectionFinalizationService.class.getMethod(
                "finalizeCollection", PreparedCollectionOutcome.class);

        assertThat(MetricCollectionService.class.getAnnotation(Transactional.class)).isNull();
        assertThat(orchestration.getAnnotation(Transactional.class)).isNull();
        assertThat(finalization.getAnnotation(Transactional.class)).isNotNull();
    }

    private void prepareActiveTarget(String vendorKey, String externalReference) {
        when(queryPort.findCollectionTarget(APPLIANCE_ID)).thenReturn(Optional.of(
                target(CollectionState.ACTIVE, vendorKey, externalReference)));
    }

    @SuppressWarnings("unchecked")
    private void executeActualVendorCallable() throws Exception {
        when(guardedExecution.execute(eq(APPLIANCE_ID), any())).thenAnswer(invocation -> {
            Callable<VendorMetricBatch> callable = invocation.getArgument(1);
            return new VendorExecutionResult.Completed<>(callable.call());
        });
    }

    private void sequenceClockAndIdentifiers(UUID... assignedIds) {
        when(clock.instant()).thenReturn(STARTED, COMPLETED);
        if (assignedIds.length > 0) {
            when(identifiers.nextIdentifier()).thenReturn(
                    assignedIds[0], java.util.Arrays.copyOfRange(assignedIds, 1, assignedIds.length));
        }
    }

    private PreparedCollectionOutcome capturePrepared() {
        ArgumentCaptor<PreparedCollectionOutcome> captor =
                ArgumentCaptor.forClass(PreparedCollectionOutcome.class);
        verify(finalizer).finalizeCollection(captor.capture());
        return captor.getValue();
    }

    private ApplianceCollectionTarget target(
            CollectionState state, String vendorKey, String externalReference) {
        return new ApplianceCollectionTarget(APPLIANCE_ID, state, vendorKey, externalReference);
    }

    private CollectApplianceCommand command() {
        return new CollectApplianceCommand(APPLIANCE_ID, CollectionTrigger.MANUAL);
    }

    private CanonicalMetricReading reading(
            CanonicalMetric metric, CanonicalUnit unit, String value) {
        return new CanonicalMetricReading(metric, unit, new BigDecimal(value));
    }

    private CollectionWarning warning(VendorMetricWarningCode code) {
        return new CollectionWarning(code.name(), code.sanitizedMessage());
    }

    private CollectionAttempt persistedAttempt(
            CollectionOutcome outcome,
            int sampleCount,
            List<CollectionWarning> warnings,
            CollectionFailure failure) {
        return new CollectionAttempt(
                ATTEMPT_ID,
                APPLIANCE_ID,
                CollectionTrigger.MANUAL,
                outcome,
                STARTED,
                COMPLETED,
                sampleCount,
                warnings,
                failure,
                outcome == CollectionOutcome.FAILED
                        ? COMPLETED.plusSeconds(60)
                        : COMPLETED.plusSeconds(30));
    }

    private static UUID uuid(long value) {
        return new UUID(0, value);
    }
}
