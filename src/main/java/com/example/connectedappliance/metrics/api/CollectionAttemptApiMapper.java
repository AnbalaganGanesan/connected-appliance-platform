package com.example.connectedappliance.metrics.api;

import com.example.connectedappliance.metrics.domain.CollectionAttempt;
import com.example.connectedappliance.metrics.domain.CollectionFailure;
import com.example.connectedappliance.metrics.domain.CollectionWarning;
import org.springframework.stereotype.Component;

/** Explicit mapping from immutable Metrics domain snapshots to public DTOs. */
@Component
public final class CollectionAttemptApiMapper {

    public CollectionAttemptResponse toResponse(CollectionAttempt attempt) {
        return new CollectionAttemptResponse(
                attempt.id(),
                attempt.applianceId(),
                attempt.trigger(),
                attempt.outcome(),
                attempt.startedAt(),
                attempt.completedAt(),
                attempt.sampleCount(),
                attempt.warnings().stream().map(this::toResponse).toList(),
                attempt.failure() == null ? null : toResponse(attempt.failure()),
                attempt.nextCollectionDueAt());
    }

    private CollectionWarningResponse toResponse(CollectionWarning warning) {
        return new CollectionWarningResponse(warning.code(), warning.message());
    }

    private CollectionFailureResponse toResponse(CollectionFailure failure) {
        return new CollectionFailureResponse(
                failure.category(), failure.message(), failure.retryAfterSeconds());
    }
}
