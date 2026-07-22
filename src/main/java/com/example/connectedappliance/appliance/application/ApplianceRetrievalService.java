package com.example.connectedappliance.appliance.application;

import java.util.Objects;
import java.util.UUID;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.example.connectedappliance.appliance.application.exception.ApplianceNotFoundException;
import com.example.connectedappliance.appliance.application.port.out.ApplianceRepository;
import com.example.connectedappliance.appliance.domain.Appliance;

/** Retrieves one appliance without exposing repository or persistence types to the API. */
@Service
public final class ApplianceRetrievalService {

    private final ApplianceRepository applianceRepository;

    public ApplianceRetrievalService(@Lazy ApplianceRepository applianceRepository) {
        this.applianceRepository = applianceRepository;
    }

    public Appliance get(UUID applianceId) {
        Objects.requireNonNull(applianceId, "applianceId must not be null");
        return applianceRepository.findById(applianceId)
                .orElseThrow(ApplianceNotFoundException::new);
    }
}
