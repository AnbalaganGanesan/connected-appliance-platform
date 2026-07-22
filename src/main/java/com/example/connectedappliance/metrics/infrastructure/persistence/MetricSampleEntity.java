package com.example.connectedappliance.metrics.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.example.connectedappliance.shared.metric.CanonicalMetric;
import com.example.connectedappliance.shared.metric.CanonicalUnit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "metric_sample")
class MetricSampleEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "appliance_id", nullable = false, updatable = false)
    private UUID applianceId;

    @Column(name = "collection_attempt_id", nullable = false, updatable = false)
    private UUID collectionAttemptId;

    @Enumerated(EnumType.STRING)
    @Column(name = "metric_name", nullable = false, length = 64, updatable = false)
    private CanonicalMetric metricName;

    @Enumerated(EnumType.STRING)
    @Column(name = "unit", nullable = false, length = 32, updatable = false)
    private CanonicalUnit unit;

    @Column(name = "value", nullable = false, precision = 20, scale = 6, updatable = false)
    private BigDecimal value;

    @Column(name = "observed_at", nullable = false, updatable = false)
    private Instant observedAt;

    @Column(name = "ingested_at", nullable = false, updatable = false)
    private Instant ingestedAt;

    protected MetricSampleEntity() {}

    MetricSampleEntity(
            UUID id,
            UUID applianceId,
            UUID collectionAttemptId,
            CanonicalMetric metricName,
            CanonicalUnit unit,
            BigDecimal value,
            Instant observedAt,
            Instant ingestedAt) {
        this.id = id;
        this.applianceId = applianceId;
        this.collectionAttemptId = collectionAttemptId;
        this.metricName = metricName;
        this.unit = unit;
        this.value = value;
        this.observedAt = observedAt;
        this.ingestedAt = ingestedAt;
    }

    UUID id() {
        return id;
    }

    UUID applianceId() {
        return applianceId;
    }

    UUID collectionAttemptId() {
        return collectionAttemptId;
    }

    CanonicalMetric metricName() {
        return metricName;
    }

    CanonicalUnit unit() {
        return unit;
    }

    BigDecimal value() {
        return value;
    }

    Instant observedAt() {
        return observedAt;
    }

    Instant ingestedAt() {
        return ingestedAt;
    }
}
