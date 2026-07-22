package com.example.connectedappliance.appliance.application.port.in;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.example.connectedappliance.appliance.domain.LastCollectionStatus;

/** Appliance-owned values calculated for one completed collection finalization. */
public record ApplianceCollectionFinalizationCommand(
        UUID applianceId,
        LastCollectionStatus lastCollectionStatus,
        int consecutiveFailureCount,
        Instant nextCollectionDueAt,
        Instant completedAt) {

    public ApplianceCollectionFinalizationCommand {
        Objects.requireNonNull(applianceId, "applianceId must not be null");
        Objects.requireNonNull(lastCollectionStatus, "lastCollectionStatus must not be null");
        Objects.requireNonNull(completedAt, "completedAt must not be null");
        if (lastCollectionStatus == LastCollectionStatus.NEVER_ATTEMPTED) {
            throw new IllegalArgumentException("NEVER_ATTEMPTED is not a finalization status");
        }
        if (consecutiveFailureCount < 0) {
            throw new IllegalArgumentException("consecutiveFailureCount must not be negative");
        }
    }
}
