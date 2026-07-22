package com.example.connectedappliance.appliance.application.port.in;

import java.util.Objects;
import java.util.UUID;

import com.example.connectedappliance.appliance.domain.CollectionState;

/** Appliance-owned snapshot containing only the state required before vendor I/O. */
public record ApplianceCollectionTarget(
        UUID applianceId,
        CollectionState collectionState,
        String vendorKey,
        String externalReference) {

    public ApplianceCollectionTarget {
        Objects.requireNonNull(applianceId, "applianceId must not be null");
        Objects.requireNonNull(collectionState, "collectionState must not be null");
        Objects.requireNonNull(vendorKey, "vendorKey must not be null");
        Objects.requireNonNull(externalReference, "externalReference must not be null");
    }
}
