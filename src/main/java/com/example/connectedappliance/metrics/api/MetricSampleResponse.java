package com.example.connectedappliance.metrics.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.example.connectedappliance.shared.metric.CanonicalMetric;
import com.example.connectedappliance.shared.metric.CanonicalUnit;

/** Public immutable representation of one persisted normalized metric sample. */
public record MetricSampleResponse(
        UUID id,
        UUID applianceId,
        UUID collectionAttemptId,
        CanonicalMetric metricName,
        CanonicalUnit unit,
        BigDecimal value,
        Instant observedAt,
        Instant ingestedAt) {

    public MetricSampleResponse {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(applianceId, "applianceId must not be null");
        Objects.requireNonNull(collectionAttemptId, "collectionAttemptId must not be null");
        Objects.requireNonNull(metricName, "metricName must not be null");
        Objects.requireNonNull(unit, "unit must not be null");
        Objects.requireNonNull(value, "value must not be null");
        Objects.requireNonNull(observedAt, "observedAt must not be null");
        Objects.requireNonNull(ingestedAt, "ingestedAt must not be null");
    }
}
