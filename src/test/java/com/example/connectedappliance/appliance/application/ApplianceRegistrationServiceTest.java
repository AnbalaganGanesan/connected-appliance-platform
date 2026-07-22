package com.example.connectedappliance.appliance.application;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.example.connectedappliance.appliance.application.exception.DuplicateApplianceException;
import com.example.connectedappliance.appliance.application.exception.UnsupportedVendorException;
import com.example.connectedappliance.appliance.application.port.out.ApplianceRepository;
import com.example.connectedappliance.appliance.application.port.out.SupportedVendorPort;
import com.example.connectedappliance.appliance.domain.Appliance;
import com.example.connectedappliance.appliance.domain.CollectionState;
import com.example.connectedappliance.appliance.domain.LastCollectionStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApplianceRegistrationServiceTest {

    private static final Instant REGISTRATION_TIME = Instant.parse("2026-07-21T10:00:00Z");

    private ApplianceRepository applianceRepository;
    private SupportedVendorPort supportedVendorPort;
    private ApplianceRegistrationService service;

    @BeforeEach
    void setUp() {
        applianceRepository = mock(ApplianceRepository.class);
        supportedVendorPort = mock(SupportedVendorPort.class);
        service = new ApplianceRegistrationService(
                applianceRepository,
                supportedVendorPort,
                Clock.fixed(REGISTRATION_TIME, ZoneOffset.UTC));
    }

    @Test
    void registersSupportedVendorWithApprovedInitialStateAndReturnsPersistedAggregate() {
        RegisterApplianceCommand command = new RegisterApplianceCommand(
                "Kitchen appliance",
                "Reviewer appliance",
                "mock-alpha",
                "  MixedCase/Reference-Aa  ",
                30);
        when(supportedVendorPort.isSupported("mock-alpha")).thenReturn(true);
        when(applianceRepository.insert(any(Appliance.class))).thenAnswer(invocation -> {
            Appliance candidate = invocation.getArgument(0);
            return copyWithVersion(candidate, 4);
        });

        Appliance registered = service.register(command);

        ArgumentCaptor<Appliance> captor = ArgumentCaptor.forClass(Appliance.class);
        verify(applianceRepository).insert(captor.capture());
        Appliance inserted = captor.getValue();
        assertThat(inserted.id()).isNotNull();
        assertThat(inserted.id()).isEqualTo(registered.id());
        assertThat(inserted.displayName()).isEqualTo("Kitchen appliance");
        assertThat(inserted.description()).isEqualTo("Reviewer appliance");
        assertThat(inserted.vendorKey()).isEqualTo("mock-alpha");
        assertThat(inserted.externalReference()).isEqualTo("  MixedCase/Reference-Aa  ");
        assertThat(inserted.collectionState()).isEqualTo(CollectionState.ACTIVE);
        assertThat(inserted.collectionIntervalSeconds()).isEqualTo(30);
        assertThat(inserted.nextCollectionDueAt()).isEqualTo(REGISTRATION_TIME.plusSeconds(30));
        assertThat(inserted.consecutiveFailureCount()).isZero();
        assertThat(inserted.lastCollectionStatus())
                .isEqualTo(LastCollectionStatus.NEVER_ATTEMPTED);
        assertThat(inserted.version()).isZero();
        assertThat(inserted.createdAt()).isEqualTo(REGISTRATION_TIME);
        assertThat(inserted.updatedAt()).isEqualTo(REGISTRATION_TIME);
        assertThat(registered.version()).isEqualTo(4);
        verify(supportedVendorPort).isSupported("mock-alpha");
    }

    @Test
    void rejectsUnsupportedVendorBeforePersistence() {
        RegisterApplianceCommand command = command("unknown-vendor", "unsupported-ref");
        when(supportedVendorPort.isSupported("unknown-vendor")).thenReturn(false);

        assertThatThrownBy(() -> service.register(command))
                .isInstanceOf(UnsupportedVendorException.class);

        verify(applianceRepository, never()).insert(any());
    }

    @Test
    void propagatesDuplicateApplianceOutcomeFromInsertion() {
        RegisterApplianceCommand command = command("mock-alpha", "duplicate-ref");
        when(supportedVendorPort.isSupported("mock-alpha")).thenReturn(true);
        when(applianceRepository.insert(any())).thenThrow(new DuplicateApplianceException());

        assertThatThrownBy(() -> service.register(command))
                .isInstanceOf(DuplicateApplianceException.class);

        verify(applianceRepository).insert(any());
    }

    private RegisterApplianceCommand command(String vendorKey, String externalReference) {
        return new RegisterApplianceCommand(
                "Appliance", null, vendorKey, externalReference, 30);
    }

    private Appliance copyWithVersion(Appliance appliance, long version) {
        return new Appliance(
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
                version,
                appliance.createdAt(),
                appliance.updatedAt());
    }
}
