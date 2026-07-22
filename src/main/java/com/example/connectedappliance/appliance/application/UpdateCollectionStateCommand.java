package com.example.connectedappliance.appliance.application;

import java.util.Objects;
import java.util.UUID;

import com.example.connectedappliance.appliance.domain.CollectionState;

/** Persistence- and transport-neutral desired collection-state replacement input. */
public record UpdateCollectionStateCommand(
        UUID applianceId, CollectionState collectionState) {

    public UpdateCollectionStateCommand {
        Objects.requireNonNull(applianceId, "applianceId must not be null");
        Objects.requireNonNull(collectionState, "collectionState must not be null");
    }
}
