package com.example.connectedappliance.appliance.application.port.in;

import java.util.Optional;
import java.util.UUID;

/** Locked Appliance operations that join the caller-owned finalization transaction. */
public interface ApplianceCollectionCommandPort {

    Optional<ApplianceCollectionFinalizationState> lockForCollectionFinalization(
            UUID applianceId);

    Optional<ApplianceCollectionFinalizationState> applyCollectionFinalization(
            ApplianceCollectionFinalizationCommand command);
}
