package com.example.connectedappliance.appliance.application;

import java.time.Clock;
import java.util.Objects;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.example.connectedappliance.appliance.application.exception.ApplianceNotFoundException;
import com.example.connectedappliance.appliance.application.port.out.ApplianceRepository;
import com.example.connectedappliance.appliance.domain.Appliance;

/** Replaces Appliance display metadata through one atomic persistence operation. */
@Service
public final class ApplianceMetadataService {

    private final ApplianceRepository applianceRepository;
    private final Clock clock;

    public ApplianceMetadataService(@Lazy ApplianceRepository applianceRepository, Clock clock) {
        this.applianceRepository = applianceRepository;
        this.clock = clock;
    }

    public Appliance replace(ReplaceApplianceMetadataCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return applianceRepository.replaceMetadata(
                        command.applianceId(),
                        command.displayName(),
                        command.description(),
                        clock.instant())
                .orElseThrow(ApplianceNotFoundException::new);
    }
}
