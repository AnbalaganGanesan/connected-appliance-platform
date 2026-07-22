package com.example.connectedappliance.metrics.application.collection;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.example.connectedappliance.metrics.domain.CollectionFailure;
import com.example.connectedappliance.metrics.domain.CollectionOutcome;
import com.example.connectedappliance.metrics.domain.CollectionTrigger;
import com.example.connectedappliance.metrics.domain.CollectionWarning;
import com.example.connectedappliance.metrics.domain.MetricSample;

/** Immutable hand-off from non-transactional vendor execution to atomic finalization. */
public record PreparedCollectionOutcome(
        UUID applianceId,
        CollectionTrigger trigger,
        UUID attemptId,
        Instant startedAt,
        Instant completedAt,
        CollectionOutcome outcome,
        List<CollectionWarning> warnings,
        CollectionFailure failure,
        List<MetricSample> samples) {

    public PreparedCollectionOutcome {
        Objects.requireNonNull(applianceId, "applianceId must not be null");
        Objects.requireNonNull(trigger, "trigger must not be null");
        Objects.requireNonNull(attemptId, "attemptId must not be null");
        Objects.requireNonNull(startedAt, "startedAt must not be null");
        Objects.requireNonNull(completedAt, "completedAt must not be null");
        Objects.requireNonNull(outcome, "outcome must not be null");
        warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings must not be null"));
        samples = List.copyOf(Objects.requireNonNull(samples, "samples must not be null"));
        if (completedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("completedAt must not be before startedAt");
        }
        for (MetricSample sample : samples) {
            if (!sample.applianceId().equals(applianceId)
                    || !sample.collectionAttemptId().equals(attemptId)) {
                throw new IllegalArgumentException("prepared sample identity does not match outcome");
            }
            if (!sample.observedAt().equals(completedAt)
                    || !sample.ingestedAt().equals(completedAt)) {
                throw new IllegalArgumentException("prepared sample timestamps must equal completedAt");
            }
        }
        switch (outcome) {
            case SUCCESS -> {
                if (samples.isEmpty() || !warnings.isEmpty() || failure != null) {
                    throw new IllegalArgumentException(
                            "SUCCESS requires samples without warnings or failure");
                }
            }
            case PARTIAL_SUCCESS -> {
                if (samples.isEmpty() || warnings.isEmpty() || failure != null) {
                    throw new IllegalArgumentException(
                            "PARTIAL_SUCCESS requires samples and warnings without failure");
                }
            }
            case FAILED -> {
                if (!samples.isEmpty() || failure == null) {
                    throw new IllegalArgumentException("FAILED requires zero samples and a failure");
                }
            }
        }
    }
}
