package com.example.connectedappliance.appliance.api;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.example.connectedappliance.appliance.application.RegisterApplianceCommand;
import com.example.connectedappliance.appliance.application.ReplaceApplianceMetadataCommand;
import com.example.connectedappliance.appliance.application.port.out.AppliancePage;
import com.example.connectedappliance.appliance.domain.Appliance;
import com.example.connectedappliance.shared.api.PageResponse;

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

    public ReplaceApplianceMetadataCommand toCommand(
            UUID applianceId, UpdateApplianceMetadataRequest request) {
        return new ReplaceApplianceMetadataCommand(
                applianceId,
                request.displayName().strip(),
                request.description() == null ? null : request.description().strip());
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

    public PageResponse<ApplianceResponse> toPageResponse(AppliancePage page) {
        return new PageResponse<>(
                page.items().stream().map(this::toResponse).toList(),
                page.page(),
                page.size(),
                page.totalElements(),
                page.totalPages());
    }
}
