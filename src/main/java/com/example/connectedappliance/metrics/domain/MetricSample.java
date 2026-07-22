package com.example.connectedappliance.metrics.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.example.connectedappliance.shared.metric.CanonicalMetric;
import com.example.connectedappliance.shared.metric.CanonicalNumericPolicy;
import com.example.connectedappliance.shared.metric.CanonicalUnit;

/** Append-only normalized metric sample. */
public record MetricSample(
        UUID id,
        UUID applianceId,
        UUID collectionAttemptId,
        CanonicalMetric metricName,
        CanonicalUnit unit,
        BigDecimal value,
        Instant observedAt,
        Instant ingestedAt) {

    public MetricSample {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(applianceId, "applianceId must not be null");
        Objects.requireNonNull(collectionAttemptId, "collectionAttemptId must not be null");
        Objects.requireNonNull(metricName, "metricName must not be null");
        Objects.requireNonNull(unit, "unit must not be null");
        Objects.requireNonNull(observedAt, "observedAt must not be null");
        Objects.requireNonNull(ingestedAt, "ingestedAt must not be null");
        metricName.validateUnit(unit);
        value = CanonicalNumericPolicy.normalize(value);
    }
}
