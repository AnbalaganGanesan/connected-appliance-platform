package com.example.connectedappliance.appliance.application;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.connectedappliance.appliance.application.exception.ApplianceNotFoundException;
import com.example.connectedappliance.appliance.application.port.out.ApplianceRepository;
import com.example.connectedappliance.appliance.domain.Appliance;
import com.example.connectedappliance.appliance.domain.CollectionState;
import com.example.connectedappliance.appliance.domain.LastCollectionStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ApplianceMetadataServiceTest {

    private static final UUID APPLIANCE_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000012");
    private static final Instant UPDATE_TIME = Instant.parse("2026-07-21T10:10:00.123456Z");

    private ApplianceRepository applianceRepository;
    private ApplianceMetadataService service;

    @BeforeEach
    void setUp() {
        applianceRepository = mock(ApplianceRepository.class);
        service = new ApplianceMetadataService(
                applianceRepository, Clock.fixed(UPDATE_TIME, ZoneOffset.UTC));
    }

    @Test
    void returnsAtomicReplacementResultAndPassesCommandAndClockValuesExactly() {
        ReplaceApplianceMetadataCommand command = new ReplaceApplianceMetadataCommand(
                APPLIANCE_ID, "Updated", "Updated description");
        Appliance persisted = appliance("Updated", "Updated description", UPDATE_TIME, 4);
        when(applianceRepository.replaceMetadata(
                        APPLIANCE_ID, "Updated", "Updated description", UPDATE_TIME))
                .thenReturn(Optional.of(persisted));

        Appliance result = service.replace(command);

        assertThat(result).isSameAs(persisted);
        verify(applianceRepository).replaceMetadata(
                APPLIANCE_ID, "Updated", "Updated description", UPDATE_TIME);
    }

    @Test
    void mapsAbsentAtomicReplacementResultToExistingNotFoundException() {
        ReplaceApplianceMetadataCommand command = new ReplaceApplianceMetadataCommand(
                APPLIANCE_ID, "Missing", null);
        when(applianceRepository.replaceMetadata(APPLIANCE_ID, "Missing", null, UPDATE_TIME))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.replace(command))
                .isInstanceOf(ApplianceNotFoundException.class);

        verify(applianceRepository).replaceMetadata(APPLIANCE_ID, "Missing", null, UPDATE_TIME);
    }

    @Test
    void rejectsNullCommandBeforeCallingPersistence() {
        assertThatThrownBy(() -> service.replace(null))
                .isInstanceOf(NullPointerException.class);
        verifyNoInteractions(applianceRepository);
    }

    private Appliance appliance(
            String displayName, String description, Instant updatedAt, long version) {
        Instant createdAt = Instant.parse("2026-07-21T10:00:00Z");
        return new Appliance(
                APPLIANCE_ID,
                displayName,
                description,
                "mock-alpha",
                "metadata-service-ref",
                CollectionState.ACTIVE,
                30,
                createdAt.plusSeconds(30),
                2,
                LastCollectionStatus.SUCCESS,
                version,
                createdAt,
                updatedAt);
    }
}
