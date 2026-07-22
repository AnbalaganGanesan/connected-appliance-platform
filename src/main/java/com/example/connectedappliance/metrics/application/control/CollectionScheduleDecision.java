package com.example.connectedappliance.metrics.application.control;

import java.time.Instant;
import java.util.Objects;

/** Calculated active-state scheduling values; it performs no Appliance mutation. */
public record CollectionScheduleDecision(
        int consecutiveFailureCount, Instant nextCollectionDueAt) {

    public CollectionScheduleDecision {
        if (consecutiveFailureCount < 0) {
            throw new IllegalArgumentException("consecutiveFailureCount must not be negative");
        }
        Objects.requireNonNull(nextCollectionDueAt, "nextCollectionDueAt must not be null");
    }
}
