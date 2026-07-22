package com.example.connectedappliance.metrics.application.control;

import java.util.Optional;
import java.util.UUID;

/** Immediate, single-process overlap coordination for one appliance. */
public interface ApplianceCollectionGuard {

    Optional<Permit> tryAcquire(UUID applianceId);

    /** Ownership token whose close operation releases only its own acquisition. */
    interface Permit extends AutoCloseable {

        @Override
        void close();
    }
}
