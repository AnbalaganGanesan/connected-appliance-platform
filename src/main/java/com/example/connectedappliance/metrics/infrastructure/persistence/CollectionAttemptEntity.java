package com.example.connectedappliance.metrics.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

import com.example.connectedappliance.metrics.domain.CollectionFailureCategory;
import com.example.connectedappliance.metrics.domain.CollectionOutcome;
import com.example.connectedappliance.metrics.domain.CollectionTrigger;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "collection_attempt")
class CollectionAttemptEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "appliance_id", nullable = false, updatable = false)
    private UUID applianceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger", nullable = false, length = 16, updatable = false)
    private CollectionTrigger trigger;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 20, updatable = false)
    private CollectionOutcome outcome;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "completed_at", nullable = false, updatable = false)
    private Instant completedAt;

    @Column(name = "sample_count", nullable = false, updatable = false)
    private int sampleCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_category", length = 20, updatable = false)
    private CollectionFailureCategory failureCategory;

    @Column(name = "failure_message", length = 500, updatable = false)
    private String failureMessage;

    @Column(name = "retry_after_seconds", updatable = false)
    private Integer retryAfterSeconds;

    @Column(name = "next_collection_due_at", updatable = false)
    private Instant nextCollectionDueAt;

    protected CollectionAttemptEntity() {}

    CollectionAttemptEntity(
            UUID id,
            UUID applianceId,
            CollectionTrigger trigger,
            CollectionOutcome outcome,
            Instant startedAt,
            Instant completedAt,
            int sampleCount,
            CollectionFailureCategory failureCategory,
            String failureMessage,
            Integer retryAfterSeconds,
            Instant nextCollectionDueAt) {
        this.id = id;
        this.applianceId = applianceId;
        this.trigger = trigger;
        this.outcome = outcome;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.sampleCount = sampleCount;
        this.failureCategory = failureCategory;
        this.failureMessage = failureMessage;
        this.retryAfterSeconds = retryAfterSeconds;
        this.nextCollectionDueAt = nextCollectionDueAt;
    }

    UUID id() {
        return id;
    }

    UUID applianceId() {
        return applianceId;
    }

    CollectionTrigger trigger() {
        return trigger;
    }

    CollectionOutcome outcome() {
        return outcome;
    }

    Instant startedAt() {
        return startedAt;
    }

    Instant completedAt() {
        return completedAt;
    }

    int sampleCount() {
        return sampleCount;
    }

    CollectionFailureCategory failureCategory() {
        return failureCategory;
    }

    String failureMessage() {
        return failureMessage;
    }

    Integer retryAfterSeconds() {
        return retryAfterSeconds;
    }

    Instant nextCollectionDueAt() {
        return nextCollectionDueAt;
    }
}
