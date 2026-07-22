package com.example.connectedappliance.metrics.application.collection;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.example.connectedappliance.appliance.application.port.in.ApplianceCollectionQueryPort;
import com.example.connectedappliance.appliance.application.port.in.ApplianceCollectionTarget;
import com.example.connectedappliance.appliance.domain.CollectionState;
import com.example.connectedappliance.metrics.application.control.ClassifiedVendorFailure;
import com.example.connectedappliance.metrics.application.control.GuardedVendorExecution;
import com.example.connectedappliance.metrics.application.control.VendorExecutionResult;
import com.example.connectedappliance.metrics.application.control.VendorFailureClassifier;
import com.example.connectedappliance.metrics.application.port.out.MetricsIdentifierGenerator;
import com.example.connectedappliance.metrics.application.port.out.VendorMetricBatch;
import com.example.connectedappliance.metrics.application.port.out.VendorMetricRequest;
import com.example.connectedappliance.metrics.application.port.out.VendorMetricSourcePort;
import com.example.connectedappliance.metrics.domain.CollectionFailure;
import com.example.connectedappliance.metrics.domain.CollectionFailureCategory;
import com.example.connectedappliance.metrics.domain.CollectionOutcome;
import com.example.connectedappliance.metrics.domain.CollectionWarning;
import com.example.connectedappliance.metrics.domain.MetricSample;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Lazy;

/** Shared non-transactional orchestration for manual and scheduled collection triggers. */
@Service
@Lazy
public class MetricCollectionService {

    private static final CollectionFailure EMPTY_BATCH_FAILURE = new CollectionFailure(
            CollectionFailureCategory.UNEXPECTED,
            VendorFailureClassifier.UNEXPECTED_MESSAGE,
            null);

    private final ApplianceCollectionQueryPort applianceQueryPort;
    private final VendorMetricSourcePort vendorMetricSourcePort;
    private final GuardedVendorExecution guardedVendorExecution;
    private final CollectionFinalizationService finalizationService;
    private final Clock clock;
    private final MetricsIdentifierGenerator identifierGenerator;

    public MetricCollectionService(
            ApplianceCollectionQueryPort applianceQueryPort,
            VendorMetricSourcePort vendorMetricSourcePort,
            GuardedVendorExecution guardedVendorExecution,
            CollectionFinalizationService finalizationService,
            Clock clock,
            MetricsIdentifierGenerator identifierGenerator) {
        this.applianceQueryPort =
                Objects.requireNonNull(applianceQueryPort, "applianceQueryPort must not be null");
        this.vendorMetricSourcePort = Objects.requireNonNull(
                vendorMetricSourcePort, "vendorMetricSourcePort must not be null");
        this.guardedVendorExecution = Objects.requireNonNull(
                guardedVendorExecution, "guardedVendorExecution must not be null");
        this.finalizationService =
                Objects.requireNonNull(finalizationService, "finalizationService must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.identifierGenerator = Objects.requireNonNull(
                identifierGenerator, "identifierGenerator must not be null");
    }

    public CollectionWorkflowResult collect(CollectApplianceCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        ApplianceCollectionTarget target = applianceQueryPort
                .findCollectionTarget(command.applianceId())
                .orElse(null);
        if (target == null) {
            return new CollectionWorkflowResult.NotFound();
        }
        if (target.collectionState() == CollectionState.PAUSED) {
            return new CollectionWorkflowResult.Paused();
        }

        VendorMetricRequest request =
                new VendorMetricRequest(target.vendorKey(), target.externalReference());
        Instant startedAt = clock.instant();
        VendorExecutionResult<VendorMetricBatch> execution = guardedVendorExecution.execute(
                command.applianceId(), () -> vendorMetricSourcePort.collect(request));
        if (execution instanceof VendorExecutionResult.Busy<VendorMetricBatch>) {
            return new CollectionWorkflowResult.Busy();
        }
        if (execution instanceof VendorExecutionResult.Saturated<VendorMetricBatch>) {
            return new CollectionWorkflowResult.Saturated();
        }

        Instant completedAt = clock.instant();
        if (completedAt.isBefore(startedAt)) {
            throw new IllegalStateException("Collection clock moved backwards.");
        }
        UUID attemptId = identifierGenerator.nextIdentifier();
        PreparedCollectionOutcome prepared = execution
                instanceof VendorExecutionResult.Completed<VendorMetricBatch> completed
                ? prepareCompleted(command, attemptId, startedAt, completedAt, completed.value())
                : prepareFailed(
                        command,
                        attemptId,
                        startedAt,
                        completedAt,
                        ((VendorExecutionResult.Failed<VendorMetricBatch>) execution).failure());
        return new CollectionWorkflowResult.Persisted(
                finalizationService.finalizeCollection(prepared));
    }

    private PreparedCollectionOutcome prepareCompleted(
            CollectApplianceCommand command,
            UUID attemptId,
            Instant startedAt,
            Instant completedAt,
            VendorMetricBatch batch) {
        List<CollectionWarning> warnings = mapWarnings(batch);
        if (batch.readings().isEmpty()) {
            return new PreparedCollectionOutcome(
                    command.applianceId(),
                    command.trigger(),
                    attemptId,
                    startedAt,
                    completedAt,
                    CollectionOutcome.FAILED,
                    warnings,
                    EMPTY_BATCH_FAILURE,
                    List.of());
        }

        List<MetricSample> samples = batch.readings().stream()
                .map(reading -> new MetricSample(
                        identifierGenerator.nextIdentifier(),
                        command.applianceId(),
                        attemptId,
                        reading.metric(),
                        reading.unit(),
                        reading.value(),
                        completedAt,
                        completedAt))
                .toList();
        CollectionOutcome outcome = warnings.isEmpty()
                ? CollectionOutcome.SUCCESS
                : CollectionOutcome.PARTIAL_SUCCESS;
        return new PreparedCollectionOutcome(
                command.applianceId(),
                command.trigger(),
                attemptId,
                startedAt,
                completedAt,
                outcome,
                warnings,
                null,
                samples);
    }

    private PreparedCollectionOutcome prepareFailed(
            CollectApplianceCommand command,
            UUID attemptId,
            Instant startedAt,
            Instant completedAt,
            ClassifiedVendorFailure failure) {
        return new PreparedCollectionOutcome(
                command.applianceId(),
                command.trigger(),
                attemptId,
                startedAt,
                completedAt,
                CollectionOutcome.FAILED,
                failure.warnings(),
                failure.failure(),
                List.of());
    }

    private List<CollectionWarning> mapWarnings(VendorMetricBatch batch) {
        return batch.warnings().stream()
                .map(warning -> new CollectionWarning(
                        warning.code().name(), warning.message()))
                .toList();
    }
}
