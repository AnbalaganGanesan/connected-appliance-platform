package com.example.connectedappliance.metrics.application.collection;

import java.util.Objects;
import java.util.UUID;

import com.example.connectedappliance.metrics.domain.CollectionTrigger;

/** Vendor-neutral request to collect one Appliance through the shared workflow. */
public record CollectApplianceCommand(UUID applianceId, CollectionTrigger trigger) {

    public CollectApplianceCommand {
        Objects.requireNonNull(applianceId, "applianceId must not be null");
        Objects.requireNonNull(trigger, "trigger must not be null");
    }
}
