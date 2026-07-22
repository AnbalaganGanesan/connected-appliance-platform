package com.example.connectedappliance.metrics.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Atomic persistence input containing one completed attempt and all of its samples. */
public record CompletedCollection(CollectionAttempt attempt, List<MetricSample> samples) {

    public CompletedCollection {
        Objects.requireNonNull(attempt, "attempt must not be null");
        samples = List.copyOf(Objects.requireNonNull(samples, "samples must not be null"));
        if (attempt.sampleCount() != samples.size()) {
            throw new IllegalArgumentException("attempt sampleCount must equal sample list size");
        }

        Set<UUID> sampleIds = new HashSet<>();
        for (MetricSample sample : samples) {
            if (!sample.applianceId().equals(attempt.applianceId())) {
                throw new IllegalArgumentException("sample applianceId must match attempt applianceId");
            }
            if (!sample.collectionAttemptId().equals(attempt.id())) {
                throw new IllegalArgumentException("sample attempt ID must match attempt ID");
            }
            if (!sampleIds.add(sample.id())) {
                throw new IllegalArgumentException("sample IDs must be unique within a collection");
            }
        }
    }
}
