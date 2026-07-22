package com.example.connectedappliance.metrics.application.collectnow;

import java.util.Objects;
import java.util.UUID;

import com.example.connectedappliance.appliance.application.exception.ApplianceNotFoundException;
import com.example.connectedappliance.metrics.application.collection.CollectApplianceCommand;
import com.example.connectedappliance.metrics.application.collection.CollectionWorkflowResult;
import com.example.connectedappliance.metrics.application.collection.MetricCollectionService;
import com.example.connectedappliance.metrics.domain.CollectionAttempt;
import com.example.connectedappliance.metrics.domain.CollectionTrigger;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/** Maps the manual collect-now use case onto the shared collection workflow exactly once. */
@Service
@Lazy
public final class CollectNowService {

    private final MetricCollectionService collectionService;

    public CollectNowService(MetricCollectionService collectionService) {
        this.collectionService =
                Objects.requireNonNull(collectionService, "collectionService must not be null");
    }

    public CollectionAttempt collectNow(UUID applianceId) {
        Objects.requireNonNull(applianceId, "applianceId must not be null");
        CollectionWorkflowResult result = collectionService.collect(
                new CollectApplianceCommand(applianceId, CollectionTrigger.MANUAL));
        if (result instanceof CollectionWorkflowResult.Persisted persisted) {
            return persisted.attempt();
        }
        if (result instanceof CollectionWorkflowResult.NotFound) {
            throw new ApplianceNotFoundException();
        }
        if (result instanceof CollectionWorkflowResult.Paused) {
            throw new AppliancePausedException();
        }
        if (result instanceof CollectionWorkflowResult.Busy) {
            throw new CollectionAlreadyInProgressException();
        }
        if (result instanceof CollectionWorkflowResult.Saturated) {
            throw new CollectionServiceUnavailableException();
        }
        throw new IllegalStateException("Unsupported collection workflow result.");
    }
}
