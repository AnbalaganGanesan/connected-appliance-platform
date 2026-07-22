package com.example.connectedappliance.appliance.application;

import java.util.Objects;
import java.util.UUID;

/** Persistence- and transport-neutral collection-interval replacement input. */
public record UpdateCollectionIntervalCommand(
        UUID applianceId, int collectionIntervalSeconds) {

    public UpdateCollectionIntervalCommand {
        Objects.requireNonNull(applianceId, "applianceId must not be null");
        if (collectionIntervalSeconds < 5 || collectionIntervalSeconds > 86_400) {
            throw new IllegalArgumentException("collectionIntervalSeconds is out of range");
        }
    }
}
