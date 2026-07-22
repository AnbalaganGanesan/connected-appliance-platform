package com.example.connectedappliance.appliance.api;

import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.example.connectedappliance.appliance.application.RegisterApplianceCommand;
import com.example.connectedappliance.appliance.domain.Appliance;
import com.example.connectedappliance.appliance.domain.CollectionState;
import com.example.connectedappliance.appliance.domain.LastCollectionStatus;

import static org.assertj.core.api.Assertions.assertThat;

class ApplianceApiMapperTest {

    private final ApplianceApiMapper mapper = new ApplianceApiMapper();

    @Test
    void mapsRequestToNormalizedCommandWhilePreservingVendorIdentity() {
        RegisterApplianceRequest request = new RegisterApplianceRequest(
                "  Kitchen appliance  ",
                "  Reviewer description  ",
                "mock-alpha",
                "  MixedCase/Reference-Aa  ",
                30);

        RegisterApplianceCommand command = mapper.toCommand(request);

        assertThat(command.displayName()).isEqualTo("Kitchen appliance");
        assertThat(command.description()).isEqualTo("Reviewer description");
        assertThat(command.vendorKey()).isEqualTo("mock-alpha");
        assertThat(command.externalReference()).isEqualTo("  MixedCase/Reference-Aa  ");
        assertThat(command.collectionIntervalSeconds()).isEqualTo(30);
    }

    @Test
    void preservesNullDescription() {
        RegisterApplianceRequest request = new RegisterApplianceRequest(
                "Appliance", null, "mock-alpha", "reference", 30);

        assertThat(mapper.toCommand(request).description()).isNull();
    }

    @Test
    void mapsEveryApprovedResponseFieldAndOmitsInternalVersion() {
        Instant createdAt = Instant.parse("2026-07-21T10:00:00Z");
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000010");
        Appliance appliance = new Appliance(
                id,
                "Kitchen appliance",
                "Description",
                "mock-alpha",
                "MixedCase-Ref",
                CollectionState.ACTIVE,
                30,
                createdAt.plusSeconds(30),
                2,
                LastCollectionStatus.FAILED,
                7,
                createdAt,
                createdAt.plusSeconds(5));

        ApplianceResponse response = mapper.toResponse(appliance);

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.displayName()).isEqualTo("Kitchen appliance");
        assertThat(response.description()).isEqualTo("Description");
        assertThat(response.vendorKey()).isEqualTo("mock-alpha");
        assertThat(response.externalReference()).isEqualTo("MixedCase-Ref");
        assertThat(response.collectionState()).isEqualTo(CollectionState.ACTIVE);
        assertThat(response.collectionIntervalSeconds()).isEqualTo(30);
        assertThat(response.nextCollectionDueAt()).isEqualTo(createdAt.plusSeconds(30));
        assertThat(response.consecutiveFailureCount()).isEqualTo(2);
        assertThat(response.lastCollectionStatus()).isEqualTo(LastCollectionStatus.FAILED);
        assertThat(response.createdAt()).isEqualTo(createdAt);
        assertThat(response.updatedAt()).isEqualTo(createdAt.plusSeconds(5));
        assertThat(Arrays.stream(ApplianceResponse.class.getRecordComponents())
                        .map(component -> component.getName()))
                .doesNotContain("version");
    }
}
