package com.example.connectedappliance.metrics.application.history;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.example.connectedappliance.appliance.application.exception.ApplianceNotFoundException;
import com.example.connectedappliance.appliance.application.port.in.ApplianceExistenceQueryPort;
import com.example.connectedappliance.metrics.application.port.out.CollectionAttemptPage;
import com.example.connectedappliance.metrics.application.port.out.CollectionAttemptQuery;
import com.example.connectedappliance.metrics.application.port.out.HistoryPageRequest;
import com.example.connectedappliance.metrics.application.port.out.MetricSamplePage;
import com.example.connectedappliance.metrics.application.port.out.MetricSampleQuery;
import com.example.connectedappliance.metrics.application.port.out.MetricsRepository;
import com.example.connectedappliance.metrics.domain.CollectionOutcome;
import com.example.connectedappliance.metrics.domain.CollectionTrigger;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/** Read-only application service for persisted attempt and normalized metric history. */
@Service
@Lazy
public final class MetricsHistoryQueryService {

    private final ApplianceExistenceQueryPort applianceExistence;
    private final MetricsRepository metricsRepository;

    public MetricsHistoryQueryService(
            ApplianceExistenceQueryPort applianceExistence,
            MetricsRepository metricsRepository) {
        this.applianceExistence = Objects.requireNonNull(
                applianceExistence, "applianceExistence must not be null");
        this.metricsRepository = Objects.requireNonNull(
                metricsRepository, "metricsRepository must not be null");
    }

    public CollectionAttemptPage getCollectionAttempts(
            UUID applianceId,
            Optional<CollectionTrigger> trigger,
            Optional<CollectionOutcome> outcome,
            int page,
            int size) {
        Objects.requireNonNull(applianceId, "applianceId must not be null");
        Objects.requireNonNull(trigger, "trigger must not be null");
        Objects.requireNonNull(outcome, "outcome must not be null");
        requireAppliance(applianceId);
        return metricsRepository.findAttempts(new CollectionAttemptQuery(
                applianceId, trigger, outcome, new HistoryPageRequest(page, size)));
    }

    public MetricSamplePage getMetricSamples(
            UUID applianceId,
            Instant fromInclusive,
            Instant toExclusive,
            int page,
            int size) {
        Objects.requireNonNull(applianceId, "applianceId must not be null");
        Objects.requireNonNull(fromInclusive, "fromInclusive must not be null");
        Objects.requireNonNull(toExclusive, "toExclusive must not be null");
        if (!fromInclusive.isBefore(toExclusive)) {
            throw new InvalidTimeRangeException();
        }
        requireAppliance(applianceId);
        return metricsRepository.findMetricSamples(new MetricSampleQuery(
                applianceId,
                fromInclusive,
                toExclusive,
                new HistoryPageRequest(page, size)));
    }

    private void requireAppliance(UUID applianceId) {
        if (!applianceExistence.exists(applianceId)) {
            throw new ApplianceNotFoundException();
        }
    }
}
