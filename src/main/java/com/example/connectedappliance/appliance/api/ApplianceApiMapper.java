package com.example.connectedappliance.appliance.api;

import org.springframework.stereotype.Component;

import com.example.connectedappliance.appliance.application.RegisterApplianceCommand;
import com.example.connectedappliance.appliance.domain.Appliance;

/** Explicit mapping between public Appliance DTOs and application/domain contracts. */
@Component
public final class ApplianceApiMapper {

    public RegisterApplianceCommand toCommand(RegisterApplianceRequest request) {
        return new RegisterApplianceCommand(
                request.displayName().strip(),
                request.description() == null ? null : request.description().strip(),
                request.vendorKey(),
                request.externalReference(),
                request.collectionIntervalSeconds());
    }

    public ApplianceResponse toResponse(Appliance appliance) {
        return new ApplianceResponse(
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
                appliance.createdAt(),
                appliance.updatedAt());
    }
}
