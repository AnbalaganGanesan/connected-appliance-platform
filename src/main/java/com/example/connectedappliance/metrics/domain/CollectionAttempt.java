package com.example.connectedappliance.metrics.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Immutable historical snapshot of one completed collection attempt. */
public record CollectionAttempt(
        UUID id,
        UUID applianceId,
        CollectionTrigger trigger,
        CollectionOutcome outcome,
        Instant startedAt,
        Instant completedAt,
        int sampleCount,
        List<CollectionWarning> warnings,
        CollectionFailure failure,
        Instant nextCollectionDueAt) {

    public CollectionAttempt {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(applianceId, "applianceId must not be null");
        Objects.requireNonNull(trigger, "trigger must not be null");
        Objects.requireNonNull(outcome, "outcome must not be null");
        Objects.requireNonNull(startedAt, "startedAt must not be null");
        Objects.requireNonNull(completedAt, "completedAt must not be null");
        warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings must not be null"));
        if (completedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("completedAt must not be before startedAt");
        }
        if (sampleCount < 0) {
            throw new IllegalArgumentException("sampleCount must not be negative");
        }
        if (nextCollectionDueAt != null && nextCollectionDueAt.isBefore(completedAt)) {
            throw new IllegalArgumentException("nextCollectionDueAt must not be before completedAt");
        }
        validateOutcome(outcome, sampleCount, warnings, failure);
    }

    private static void validateOutcome(
            CollectionOutcome outcome,
            int sampleCount,
            List<CollectionWarning> warnings,
            CollectionFailure failure) {
        switch (outcome) {
            case SUCCESS -> {
                if (sampleCount == 0 || !warnings.isEmpty() || failure != null) {
                    throw new IllegalArgumentException(
                            "SUCCESS requires samples without warnings or failure");
                }
            }
            case PARTIAL_SUCCESS -> {
                if (sampleCount == 0 || warnings.isEmpty() || failure != null) {
                    throw new IllegalArgumentException(
                            "PARTIAL_SUCCESS requires samples and warnings without failure");
                }
            }
            case FAILED -> {
                if (sampleCount != 0 || failure == null) {
                    throw new IllegalArgumentException(
                            "FAILED requires zero samples and a failure");
                }
            }
        }
    }
}
