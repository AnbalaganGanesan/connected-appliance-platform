package com.example.connectedappliance.appliance.application;

import java.time.Clock;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ApplianceCollectionConfigurationServiceTest {

    private static final UUID APPLIANCE_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000013");
    private static final Instant UPDATE_TIME = Instant.parse("2026-07-21T10:10:00.123456Z");

    private ApplianceRepository repository;
    private Clock clock;
    private ApplianceCollectionConfigurationService service;

    @BeforeEach
    void setUp() {
        repository = mock(ApplianceRepository.class);
        clock = mock(Clock.class);
        when(clock.instant()).thenReturn(UPDATE_TIME);
        service = new ApplianceCollectionConfigurationService(repository, clock);
    }

    @Test
    void delegatesIntervalReplacementWithOneClockValueAndReturnsAtomicResult() {
        UpdateCollectionIntervalCommand command =
                new UpdateCollectionIntervalCommand(APPLIANCE_ID, 60);
        Appliance persisted = appliance(CollectionState.ACTIVE, 60, UPDATE_TIME.plusSeconds(60));
        when(repository.replaceCollectionInterval(APPLIANCE_ID, 60, UPDATE_TIME))
                .thenReturn(Optional.of(persisted));

        Appliance result = service.updateCollectionInterval(command);

        assertThat(result).isSameAs(persisted);
        verify(repository).replaceCollectionInterval(APPLIANCE_ID, 60, UPDATE_TIME);
        verify(clock).instant();
    }

    @Test
    void mapsMissingIntervalTargetToExistingNotFoundException() {
        when(repository.replaceCollectionInterval(APPLIANCE_ID, 60, UPDATE_TIME))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateCollectionInterval(
                        new UpdateCollectionIntervalCommand(APPLIANCE_ID, 60)))
                .isInstanceOf(ApplianceNotFoundException.class);

        verify(repository).replaceCollectionInterval(APPLIANCE_ID, 60, UPDATE_TIME);
        verify(clock).instant();
    }

    @Test
    void delegatesStateReplacementWithOneClockValueAndReturnsAtomicResult() {
        UpdateCollectionStateCommand command =
                new UpdateCollectionStateCommand(APPLIANCE_ID, CollectionState.PAUSED);
        Appliance persisted = appliance(CollectionState.PAUSED, 30, null);
        when(repository.replaceCollectionState(
                        APPLIANCE_ID, CollectionState.PAUSED, UPDATE_TIME))
                .thenReturn(Optional.of(persisted));

        Appliance result = service.updateCollectionState(command);

        assertThat(result).isSameAs(persisted);
        verify(repository).replaceCollectionState(
                APPLIANCE_ID, CollectionState.PAUSED, UPDATE_TIME);
        verify(clock).instant();
    }

    @Test
    void mapsMissingStateTargetToExistingNotFoundException() {
        when(repository.replaceCollectionState(
                        APPLIANCE_ID, CollectionState.ACTIVE, UPDATE_TIME))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateCollectionState(
                        new UpdateCollectionStateCommand(APPLIANCE_ID, CollectionState.ACTIVE)))
                .isInstanceOf(ApplianceNotFoundException.class);

        verify(repository).replaceCollectionState(
                APPLIANCE_ID, CollectionState.ACTIVE, UPDATE_TIME);
        verify(clock).instant();
    }

    @Test
    void rejectsNullCommandsBeforeCallingPersistence() {
        assertThatThrownBy(() -> service.updateCollectionInterval(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> service.updateCollectionState(null))
                .isInstanceOf(NullPointerException.class);
        verifyNoInteractions(repository, clock);
    }

    private Appliance appliance(CollectionState state, int interval, Instant dueAt) {
        Instant createdAt = Instant.parse("2026-07-21T10:00:00Z");
        return new Appliance(
                APPLIANCE_ID,
                "Appliance",
                "Description",
                "mock-alpha",
                "configuration-service-ref",
                state,
                interval,
                dueAt,
                2,
                LastCollectionStatus.SUCCESS,
                4,
                createdAt,
                UPDATE_TIME);
    }
}
