package com.example.connectedappliance.appliance.application.port.in;

import java.util.Optional;
import java.util.UUID;

/** Non-locking Appliance query used for collection eligibility before vendor I/O. */
public interface ApplianceCollectionQueryPort {

    Optional<ApplianceCollectionTarget> findCollectionTarget(UUID applianceId);
}
