package com.example.connectedappliance.appliance.infrastructure.persistence;

import com.example.connectedappliance.appliance.domain.Appliance;

final class AppliancePersistenceMapper {

    ApplianceEntity toEntity(Appliance appliance) {
        return new ApplianceEntity(
                appliance.id(),
                appliance.displayName(),
                appliance.description(),
                appliance.vendorKey(),
                appliance.externalReference(),
                appliance.collectionState(),
                appliance.collectionIntervalSeconds(),
                appliance.nextCollectionDueAt(),
                appliance.consecutiveFailureCount(),
                appliance.lastCollectionStatus(),
                appliance.version(),
                appliance.createdAt(),
                appliance.updatedAt());
    }

    Appliance toDomain(ApplianceEntity entity) {
        return new Appliance(
                entity.id(),
                entity.displayName(),
                entity.description(),
                entity.vendorKey(),
                entity.externalReference(),
                entity.collectionState(),
                entity.collectionIntervalSeconds(),
                entity.nextCollectionDueAt(),
                entity.consecutiveFailureCount(),
                entity.lastCollectionStatus(),
                entity.version(),
                entity.createdAt(),
                entity.updatedAt());
    }
}
