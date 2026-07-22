package com.example.connectedappliance.appliance.application;

import java.time.Instant;
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
import static org.mockito.Mockito.when;

class ApplianceRetrievalServiceTest {

    private ApplianceRepository applianceRepository;
    private ApplianceRetrievalService service;

    @BeforeEach
    void setUp() {
        applianceRepository = mock(ApplianceRepository.class);
        service = new ApplianceRetrievalService(applianceRepository);
    }

    @Test
    void returnsExistingApplianceForExactIdentifier() {
        Appliance expected = appliance();
        when(applianceRepository.findById(expected.id())).thenReturn(Optional.of(expected));

        assertThat(service.get(expected.id())).isSameAs(expected);
        verify(applianceRepository).findById(expected.id());
    }

    @Test
    void throwsFocusedOutcomeWhenApplianceDoesNotExist() {
        UUID missingId = UUID.fromString("00000000-0000-0000-0000-000000000099");
        when(applianceRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(missingId))
                .isInstanceOf(ApplianceNotFoundException.class);
        verify(applianceRepository).findById(missingId);
    }

    private Appliance appliance() {
        Instant createdAt = Instant.parse("2026-07-21T10:00:00Z");
        return new Appliance(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "Appliance",
                null,
                "mock-alpha",
                "retrieval-ref",
                CollectionState.ACTIVE,
                30,
                createdAt.plusSeconds(30),
                0,
                LastCollectionStatus.NEVER_ATTEMPTED,
                0,
                createdAt,
                createdAt);
    }
}
