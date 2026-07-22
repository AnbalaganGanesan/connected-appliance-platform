package com.example.connectedappliance.appliance.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.example.connectedappliance.appliance.domain.Appliance;
import com.example.connectedappliance.appliance.domain.CollectionState;

/** Appliance-owned persistence operations required by approved application workflows. */
public interface ApplianceRepository {

    Appliance insert(Appliance appliance);

    Optional<Appliance> findById(UUID id);

    Optional<Appliance> replaceMetadata(
            UUID id, String displayName, String description, Instant changedAt);

    Optional<Appliance> replaceCollectionInterval(
            UUID id, int collectionIntervalSeconds, Instant changedAt);

    Optional<Appliance> replaceCollectionState(
            UUID id, CollectionState collectionState, Instant changedAt);

    AppliancePage findAll(
            AppliancePageRequest pageRequest, Optional<CollectionState> collectionState);

    List<Appliance> findDue(Instant cutoffInclusive, int limit);
}
