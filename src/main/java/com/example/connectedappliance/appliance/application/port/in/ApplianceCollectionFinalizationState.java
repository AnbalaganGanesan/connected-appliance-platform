package com.example.connectedappliance.appliance.application.port.in;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.example.connectedappliance.appliance.domain.CollectionState;
import com.example.connectedappliance.appliance.domain.LastCollectionStatus;

/** Latest locked Appliance state required to calculate and verify collection finalization. */
public record ApplianceCollectionFinalizationState(
        UUID applianceId,
        CollectionState collectionState,
        int collectionIntervalSeconds,
        int consecutiveFailureCount,
        LastCollectionStatus lastCollectionStatus,
        Instant nextCollectionDueAt) {

    public ApplianceCollectionFinalizationState {
        Objects.requireNonNull(applianceId, "applianceId must not be null");
        Objects.requireNonNull(collectionState, "collectionState must not be null");
        Objects.requireNonNull(lastCollectionStatus, "lastCollectionStatus must not be null");
        if (collectionIntervalSeconds < 5 || collectionIntervalSeconds > 86_400) {
            throw new IllegalArgumentException("collectionIntervalSeconds is out of range");
        }
        if (consecutiveFailureCount < 0) {
            throw new IllegalArgumentException("consecutiveFailureCount must not be negative");
        }
        if (collectionState == CollectionState.ACTIVE && nextCollectionDueAt == null) {
            throw new IllegalArgumentException("ACTIVE state requires a due time");
        }
        if (collectionState == CollectionState.PAUSED && nextCollectionDueAt != null) {
            throw new IllegalArgumentException("PAUSED state requires a null due time");
        }
    }
}
