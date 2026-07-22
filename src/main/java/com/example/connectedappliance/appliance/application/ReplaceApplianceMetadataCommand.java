package com.example.connectedappliance.appliance.application;

import java.util.Objects;
import java.util.UUID;

/** Persistence- and transport-neutral display-metadata replacement input. */
public record ReplaceApplianceMetadataCommand(
        UUID applianceId, String displayName, String description) {

    public ReplaceApplianceMetadataCommand {
        Objects.requireNonNull(applianceId, "applianceId must not be null");
        Objects.requireNonNull(displayName, "displayName must not be null");
    }
}
