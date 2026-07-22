package com.example.connectedappliance.metrics.application.port.out;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.example.connectedappliance.metrics.domain.CollectionOutcome;
import com.example.connectedappliance.metrics.domain.CollectionTrigger;

/** Persistence-neutral filter and page request for one appliance's attempt history. */
public record CollectionAttemptQuery(
        UUID applianceId,
        Optional<CollectionTrigger> trigger,
        Optional<CollectionOutcome> outcome,
        HistoryPageRequest pageRequest) {

    public CollectionAttemptQuery {
        Objects.requireNonNull(applianceId, "applianceId must not be null");
        Objects.requireNonNull(trigger, "trigger must not be null");
        Objects.requireNonNull(outcome, "outcome must not be null");
        Objects.requireNonNull(pageRequest, "pageRequest must not be null");
    }
}
