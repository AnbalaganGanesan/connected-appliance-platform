package com.example.connectedappliance.metrics.application.collection;

import java.time.Instant;
import java.util.Objects;

import com.example.connectedappliance.appliance.application.port.in.ApplianceCollectionCommandPort;
import com.example.connectedappliance.appliance.application.port.in.ApplianceCollectionFinalizationCommand;
import com.example.connectedappliance.appliance.application.port.in.ApplianceCollectionFinalizationState;
import com.example.connectedappliance.appliance.domain.CollectionState;
import com.example.connectedappliance.appliance.domain.LastCollectionStatus;
import com.example.connectedappliance.metrics.application.control.ClassifiedVendorFailure;
import com.example.connectedappliance.metrics.application.control.CollectionScheduleDecision;
import com.example.connectedappliance.metrics.application.control.CollectionSchedulingPolicy;
import com.example.connectedappliance.metrics.application.port.out.MetricsRepository;
import com.example.connectedappliance.metrics.domain.CollectionAttempt;
import com.example.connectedappliance.metrics.domain.CollectionOutcome;
import com.example.connectedappliance.metrics.domain.CompletedCollection;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Lazy;
import org.springframework.transaction.annotation.Transactional;

/** Owns the single short transaction that finalizes Metrics and latest Appliance state. */
@Service
@Lazy
public class CollectionFinalizationService {

    private final ApplianceCollectionCommandPort applianceCommandPort;
    private final MetricsRepository metricsRepository;
    private final CollectionSchedulingPolicy schedulingPolicy;

    public CollectionFinalizationService(
            ApplianceCollectionCommandPort applianceCommandPort,
            MetricsRepository metricsRepository,
            CollectionSchedulingPolicy schedulingPolicy) {
        this.applianceCommandPort = Objects.requireNonNull(
                applianceCommandPort, "applianceCommandPort must not be null");
        this.metricsRepository =
                Objects.requireNonNull(metricsRepository, "metricsRepository must not be null");
        this.schedulingPolicy =
                Objects.requireNonNull(schedulingPolicy, "schedulingPolicy must not be null");
    }

    @Transactional
    public CollectionAttempt finalizeCollection(PreparedCollectionOutcome prepared) {
        Objects.requireNonNull(prepared, "prepared must not be null");
        ApplianceCollectionFinalizationState latest = applianceCommandPort
                .lockForCollectionFinalization(prepared.applianceId())
                .orElseThrow(CollectionFinalizationException::new);

        CollectionScheduleDecision schedule = schedule(prepared, latest);
        Instant finalDue = latest.collectionState() == CollectionState.PAUSED
                ? null
                : schedule.nextCollectionDueAt();
        LastCollectionStatus finalStatus = mapStatus(prepared.outcome());
        CollectionAttempt attempt = new CollectionAttempt(
                prepared.attemptId(),
                prepared.applianceId(),
                prepared.trigger(),
                prepared.outcome(),
                prepared.startedAt(),
                prepared.completedAt(),
                prepared.samples().size(),
                prepared.warnings(),
                prepared.failure(),
                finalDue);
        CollectionAttempt persisted =
                metricsRepository.insert(new CompletedCollection(attempt, prepared.samples()));
        ApplianceCollectionFinalizationCommand command =
                new ApplianceCollectionFinalizationCommand(
                        prepared.applianceId(),
                        finalStatus,
                        schedule.consecutiveFailureCount(),
                        finalDue,
                        prepared.completedAt());
        applianceCommandPort
                .applyCollectionFinalization(command)
                .orElseThrow(CollectionFinalizationException::new);
        return persisted;
    }

    private CollectionScheduleDecision schedule(
            PreparedCollectionOutcome prepared,
            ApplianceCollectionFinalizationState latest) {
        return switch (prepared.outcome()) {
            case SUCCESS -> schedulingPolicy.afterSuccess(
                    prepared.completedAt(), latest.collectionIntervalSeconds());
            case PARTIAL_SUCCESS -> schedulingPolicy.afterPartialSuccess(
                    prepared.completedAt(), latest.collectionIntervalSeconds());
            case FAILED -> schedulingPolicy.afterFailure(
                    prepared.completedAt(),
                    latest.collectionIntervalSeconds(),
                    latest.consecutiveFailureCount(),
                    new ClassifiedVendorFailure(prepared.failure(), prepared.warnings()));
        };
    }

    private LastCollectionStatus mapStatus(CollectionOutcome outcome) {
        return switch (outcome) {
            case SUCCESS -> LastCollectionStatus.SUCCESS;
            case PARTIAL_SUCCESS -> LastCollectionStatus.PARTIAL_SUCCESS;
            case FAILED -> LastCollectionStatus.FAILED;
        };
    }
}
