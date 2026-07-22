package com.example.connectedappliance.appliance.application;

/** Persistence- and transport-neutral input for appliance registration. */
public record RegisterApplianceCommand(
        String displayName,
        String description,
        String vendorKey,
        String externalReference,
        int collectionIntervalSeconds) {
}
