package com.example.connectedappliance.metrics.infrastructure.persistence;

import java.util.List;
import java.util.stream.IntStream;

import com.example.connectedappliance.metrics.domain.CollectionAttempt;
import com.example.connectedappliance.metrics.domain.CollectionFailure;
import com.example.connectedappliance.metrics.domain.CollectionWarning;
import com.example.connectedappliance.metrics.domain.CompletedCollection;
import com.example.connectedappliance.metrics.domain.MetricSample;

final class MetricsPersistenceMapper {

    CollectionAttemptEntity toAttemptEntity(CompletedCollection completedCollection) {
        CollectionAttempt attempt = completedCollection.attempt();
        CollectionFailure failure = attempt.failure();
        return new CollectionAttemptEntity(
                attempt.id(),
                attempt.applianceId(),
                attempt.trigger(),
                attempt.outcome(),
                attempt.startedAt(),
                attempt.completedAt(),
                attempt.sampleCount(),
                failure == null ? null : failure.category(),
                failure == null ? null : failure.message(),
                failure == null ? null : failure.retryAfterSeconds(),
                attempt.nextCollectionDueAt());
    }

    List<CollectionWarningEntity> toWarningEntities(CompletedCollection completedCollection) {
        List<CollectionWarning> warnings = completedCollection.attempt().warnings();
        return IntStream.range(0, warnings.size())
                .mapToObj(index -> new CollectionWarningEntity(
                        completedCollection.attempt().id(),
                        index,
                        warnings.get(index).code(),
                        warnings.get(index).message()))
                .toList();
    }

    List<MetricSampleEntity> toSampleEntities(CompletedCollection completedCollection) {
        return completedCollection.samples().stream().map(this::toEntity).toList();
    }

    CollectionAttempt toDomain(
            CollectionAttemptEntity entity, List<CollectionWarningEntity> warningEntities) {
        CollectionFailure failure = entity.failureCategory() == null
                ? null
                : new CollectionFailure(
                        entity.failureCategory(),
                        entity.failureMessage(),
                        entity.retryAfterSeconds());
        List<CollectionWarning> warnings = warningEntities.stream()
                .sorted((left, right) -> Integer.compare(left.warningIndex(), right.warningIndex()))
                .map(warning -> new CollectionWarning(warning.code(), warning.message()))
                .toList();
        return new CollectionAttempt(
                entity.id(),
                entity.applianceId(),
                entity.trigger(),
                entity.outcome(),
                entity.startedAt(),
                entity.completedAt(),
                entity.sampleCount(),
                warnings,
                failure,
                entity.nextCollectionDueAt());
    }

    MetricSample toDomain(MetricSampleEntity entity) {
        return new MetricSample(
                entity.id(),
                entity.applianceId(),
                entity.collectionAttemptId(),
                entity.metricName(),
                entity.unit(),
                entity.value(),
                entity.observedAt(),
                entity.ingestedAt());
    }

    private MetricSampleEntity toEntity(MetricSample sample) {
        return new MetricSampleEntity(
                sample.id(),
                sample.applianceId(),
                sample.collectionAttemptId(),
                sample.metricName(),
                sample.unit(),
                sample.value(),
                sample.observedAt(),
                sample.ingestedAt());
    }
}
