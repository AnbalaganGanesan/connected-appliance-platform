package com.example.connectedappliance.appliance.application;

import java.util.Objects;
import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.example.connectedappliance.appliance.application.port.out.AppliancePage;
import com.example.connectedappliance.appliance.application.port.out.AppliancePageRequest;
import com.example.connectedappliance.appliance.application.port.out.ApplianceRepository;
import com.example.connectedappliance.appliance.domain.CollectionState;

/** Lists appliances through the Appliance-owned persistence port. */
@Service
public final class ApplianceListingService {

    private final ApplianceRepository applianceRepository;

    public ApplianceListingService(@Lazy ApplianceRepository applianceRepository) {
        this.applianceRepository = applianceRepository;
    }

    public AppliancePage list(
            int page, int size, Optional<CollectionState> collectionState) {
        Objects.requireNonNull(collectionState, "collectionState must not be null");
        return applianceRepository.findAll(
                new AppliancePageRequest(page, size), collectionState);
    }
}
