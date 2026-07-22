package com.example.connectedappliance.appliance.api;

import java.time.Instant;
import java.util.UUID;

import com.example.connectedappliance.appliance.domain.CollectionState;
import com.example.connectedappliance.appliance.domain.LastCollectionStatus;

/** DTO-only public appliance representation; internal optimistic version is intentionally omitted. */
public record ApplianceResponse(
        UUID id,
        String displayName,
        String description,
        String vendorKey,
        String externalReference,
        CollectionState collectionState,
        int collectionIntervalSeconds,
        Instant nextCollectionDueAt,
        int consecutiveFailureCount,
        LastCollectionStatus lastCollectionStatus,
        Instant createdAt,
        Instant updatedAt) {
}
