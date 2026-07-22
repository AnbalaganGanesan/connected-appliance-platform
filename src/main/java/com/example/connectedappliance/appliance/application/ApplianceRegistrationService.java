package com.example.connectedappliance.appliance.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.example.connectedappliance.appliance.application.exception.UnsupportedVendorException;
import com.example.connectedappliance.appliance.application.port.out.ApplianceRepository;
import com.example.connectedappliance.appliance.application.port.out.SupportedVendorPort;
import com.example.connectedappliance.appliance.domain.Appliance;
import com.example.connectedappliance.appliance.domain.CollectionState;
import com.example.connectedappliance.appliance.domain.LastCollectionStatus;

/** Registers an appliance through consumer-owned ports and application-generated identity/time. */
@Service
public final class ApplianceRegistrationService {

    private final ApplianceRepository applianceRepository;
    private final SupportedVendorPort supportedVendorPort;
    private final Clock clock;

    public ApplianceRegistrationService(
            @Lazy ApplianceRepository applianceRepository,
            SupportedVendorPort supportedVendorPort,
            Clock clock) {
        this.applianceRepository = applianceRepository;
        this.supportedVendorPort = supportedVendorPort;
        this.clock = clock;
    }

    public Appliance register(RegisterApplianceCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        if (!supportedVendorPort.isSupported(command.vendorKey())) {
            throw new UnsupportedVendorException();
        }

        Instant registrationTime = clock.instant();
        Appliance appliance = new Appliance(
                UUID.randomUUID(),
                command.displayName(),
                command.description(),
                command.vendorKey(),
                command.externalReference(),
                CollectionState.ACTIVE,
                command.collectionIntervalSeconds(),
                registrationTime.plusSeconds(command.collectionIntervalSeconds()),
                0,
                LastCollectionStatus.NEVER_ATTEMPTED,
                0,
                registrationTime,
                registrationTime);
        return applianceRepository.insert(appliance);
    }
}
