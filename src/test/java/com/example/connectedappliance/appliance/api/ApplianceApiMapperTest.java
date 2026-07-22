package com.example.connectedappliance.appliance.api;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.example.connectedappliance.appliance.application.RegisterApplianceCommand;
import com.example.connectedappliance.appliance.application.ReplaceApplianceMetadataCommand;
import com.example.connectedappliance.appliance.application.port.out.AppliancePage;
import com.example.connectedappliance.appliance.domain.Appliance;
import com.example.connectedappliance.appliance.domain.CollectionState;
import com.example.connectedappliance.appliance.domain.LastCollectionStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    void mapsMetadataRequestToNormalizedCommandAndPreservesUuid() {
        UUID applianceId = UUID.fromString("00000000-0000-0000-0000-000000000042");
        UpdateApplianceMetadataRequest request = new UpdateApplianceMetadataRequest(
                "  Kitchen  ", "  Ground floor  ");

        ReplaceApplianceMetadataCommand command = mapper.toCommand(applianceId, request);

        assertThat(command.applianceId()).isEqualTo(applianceId);
        assertThat(command.displayName()).isEqualTo("Kitchen");
        assertThat(command.description()).isEqualTo("Ground floor");
    }

    @Test
    void mapsNullOrOmittedMetadataDescriptionAsClear() {
        UUID applianceId = UUID.fromString("00000000-0000-0000-0000-000000000043");

        ReplaceApplianceMetadataCommand command = mapper.toCommand(
                applianceId, new UpdateApplianceMetadataRequest("Kitchen", null));

        assertThat(command.applianceId()).isEqualTo(applianceId);
        assertThat(command.description()).isNull();
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

    @Test
    void mapsPopulatedPageWithoutRecalculatingMetadataAndPreservesItemOrder() {
        Appliance first = appliance(
                "00000000-0000-0000-0000-000000000001", "first", 1);
        Appliance second = appliance(
                "00000000-0000-0000-0000-000000000002", "second", 2);
        AppliancePage source = new AppliancePage(List.of(first, second), 2, 10, 25, 3);

        var response = mapper.toPageResponse(source);

        assertThat(response.items()).extracting(ApplianceResponse::id)
                .containsExactly(first.id(), second.id());
        assertThat(response.page()).isEqualTo(2);
        assertThat(response.size()).isEqualTo(10);
        assertThat(response.totalElements()).isEqualTo(25);
        assertThat(response.totalPages()).isEqualTo(3);
        assertThatThrownBy(() -> response.items().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(response.items()).allSatisfy(item -> assertThat(
                        Arrays.stream(item.getClass().getRecordComponents())
                                .map(component -> component.getName()))
                .doesNotContain("version"));
    }

    @Test
    void mapsEmptyPageToImmutableEmptyItemsWithRequestedMetadata() {
        AppliancePage source = new AppliancePage(List.of(), 4, 20, 3, 1);

        var response = mapper.toPageResponse(source);

        assertThat(response.items()).isEmpty();
        assertThat(response.page()).isEqualTo(4);
        assertThat(response.size()).isEqualTo(20);
        assertThat(response.totalElements()).isEqualTo(3);
        assertThat(response.totalPages()).isOne();
        assertThatThrownBy(() -> response.items().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private Appliance appliance(String id, String reference, long version) {
        Instant createdAt = Instant.parse("2026-07-21T10:00:00Z");
        return new Appliance(
                UUID.fromString(id),
                "Appliance " + reference,
                null,
                "mock-alpha",
                reference,
                CollectionState.ACTIVE,
                30,
                createdAt.plusSeconds(30),
                0,
                LastCollectionStatus.NEVER_ATTEMPTED,
                version,
                createdAt,
                createdAt);
    }

}
