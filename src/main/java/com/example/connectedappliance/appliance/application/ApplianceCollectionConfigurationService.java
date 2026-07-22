package com.example.connectedappliance.appliance.application;

import java.time.Clock;
import java.util.Objects;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.example.connectedappliance.appliance.application.exception.ApplianceNotFoundException;
import com.example.connectedappliance.appliance.application.port.out.ApplianceRepository;
import com.example.connectedappliance.appliance.domain.Appliance;

/** Replaces Appliance collection interval and desired state through atomic mutations. */
@Service
public final class ApplianceCollectionConfigurationService {

    private final ApplianceRepository applianceRepository;
    private final Clock clock;

    public ApplianceCollectionConfigurationService(
            @Lazy ApplianceRepository applianceRepository, Clock clock) {
        this.applianceRepository = applianceRepository;
        this.clock = clock;
    }

    public Appliance updateCollectionInterval(UpdateCollectionIntervalCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return applianceRepository.replaceCollectionInterval(
                        command.applianceId(),
                        command.collectionIntervalSeconds(),
                        clock.instant())
                .orElseThrow(ApplianceNotFoundException::new);
    }

    public Appliance updateCollectionState(UpdateCollectionStateCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return applianceRepository.replaceCollectionState(
                        command.applianceId(), command.collectionState(), clock.instant())
                .orElseThrow(ApplianceNotFoundException::new);
    }
}
