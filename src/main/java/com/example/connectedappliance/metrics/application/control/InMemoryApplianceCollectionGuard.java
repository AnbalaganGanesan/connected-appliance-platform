package com.example.connectedappliance.metrics.application.control;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Non-blocking per-appliance guard for the approved single-instance deployment. */
public final class InMemoryApplianceCollectionGuard implements ApplianceCollectionGuard {

    private final ConcurrentHashMap<UUID, Object> owners = new ConcurrentHashMap<>();

    @Override
    public Optional<Permit> tryAcquire(UUID applianceId) {
        Objects.requireNonNull(applianceId, "applianceId must not be null");
        Object ownershipToken = new Object();
        if (owners.putIfAbsent(applianceId, ownershipToken) != null) {
            return Optional.empty();
        }
        return Optional.of(new OwnedPermit(applianceId, ownershipToken));
    }

    private final class OwnedPermit implements Permit {

        private final UUID applianceId;
        private final Object ownershipToken;
        private final AtomicBoolean closed = new AtomicBoolean();

        private OwnedPermit(UUID applianceId, Object ownershipToken) {
            this.applianceId = applianceId;
            this.ownershipToken = ownershipToken;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                owners.remove(applianceId, ownershipToken);
            }
        }
    }
}
