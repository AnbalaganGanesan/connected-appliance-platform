package com.example.connectedappliance.metrics.api;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.example.connectedappliance.metrics.domain.CollectionOutcome;
import com.example.connectedappliance.metrics.domain.CollectionTrigger;

/** Public immutable representation of one persisted completed collection attempt. */
public record CollectionAttemptResponse(
        UUID id,
        UUID applianceId,
        CollectionTrigger trigger,
        CollectionOutcome outcome,
        Instant startedAt,
        Instant completedAt,
        int sampleCount,
        List<CollectionWarningResponse> warnings,
        CollectionFailureResponse failure,
        Instant nextCollectionDueAt) {

    public CollectionAttemptResponse {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(applianceId, "applianceId must not be null");
        Objects.requireNonNull(trigger, "trigger must not be null");
        Objects.requireNonNull(outcome, "outcome must not be null");
        Objects.requireNonNull(startedAt, "startedAt must not be null");
        Objects.requireNonNull(completedAt, "completedAt must not be null");
        warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings must not be null"));
    }
}
