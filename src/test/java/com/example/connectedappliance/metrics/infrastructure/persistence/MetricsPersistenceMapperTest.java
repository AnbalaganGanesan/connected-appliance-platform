package com.example.connectedappliance.metrics.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.example.connectedappliance.metrics.domain.CollectionAttempt;
import com.example.connectedappliance.metrics.domain.CollectionFailure;
import com.example.connectedappliance.metrics.domain.CollectionFailureCategory;
import com.example.connectedappliance.metrics.domain.CollectionOutcome;
import com.example.connectedappliance.metrics.domain.CollectionTrigger;
import com.example.connectedappliance.metrics.domain.CollectionWarning;
import com.example.connectedappliance.metrics.domain.CompletedCollection;
import com.example.connectedappliance.metrics.domain.MetricSample;
import com.example.connectedappliance.shared.metric.CanonicalMetric;
import com.example.connectedappliance.shared.metric.CanonicalUnit;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MetricsPersistenceMapperTest {

    private static final Instant START = Instant.parse("2026-07-21T10:00:00Z");
    private static final Instant COMPLETE = START.plusSeconds(2);
    private final MetricsPersistenceMapper mapper = new MetricsPersistenceMapper();

    @Test
    void mapsSuccessAttemptAndMetricSampleWithoutChangingAssignedValues() {
        UUID applianceId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        Instant due = COMPLETE.plusSeconds(30);
        CollectionAttempt attempt = new CollectionAttempt(
                attemptId, applianceId, CollectionTrigger.MANUAL, CollectionOutcome.SUCCESS,
                START, COMPLETE, 1, List.of(), null, due);
        MetricSample sample = new MetricSample(
                UUID.randomUUID(), applianceId, attemptId, CanonicalMetric.TEMPERATURE,
                CanonicalUnit.CELSIUS, new BigDecimal("21.500000"), START.plusSeconds(1), COMPLETE);
        CompletedCollection completedCollection = new CompletedCollection(attempt, List.of(sample));

        CollectionAttemptEntity attemptEntity = mapper.toAttemptEntity(completedCollection);
        MetricSampleEntity sampleEntity = mapper.toSampleEntities(completedCollection).get(0);

        assertThat(mapper.toDomain(attemptEntity, List.of())).isEqualTo(attempt);
        assertThat(mapper.toDomain(sampleEntity)).isEqualTo(sample);
        assertThat(mapper.toDomain(sampleEntity).value()).isEqualTo(new BigDecimal("21.500000"));
        assertThat(mapper.toDomain(sampleEntity).value().scale()).isEqualTo(6);
    }

    @Test
    void mapsPartialAttemptWarningsInStoredIndexOrder() {
        UUID applianceId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        List<CollectionWarning> warnings = List.of(
                new CollectionWarning("UNKNOWN_METRIC", "first"),
                new CollectionWarning("MALFORMED_VALUE", "second"));
        CollectionAttempt attempt = new CollectionAttempt(
                attemptId, applianceId, CollectionTrigger.SCHEDULED,
                CollectionOutcome.PARTIAL_SUCCESS, START, COMPLETE, 1, warnings, null, null);
        MetricSample sample = new MetricSample(
                UUID.randomUUID(), applianceId, attemptId, CanonicalMetric.POWER,
                CanonicalUnit.WATT, new BigDecimal("125.000000"), START, COMPLETE);
        CompletedCollection collection = new CompletedCollection(attempt, List.of(sample));

        List<CollectionWarningEntity> entities = mapper.toWarningEntities(collection);
        CollectionAttempt mapped = mapper.toDomain(
                mapper.toAttemptEntity(collection), List.of(entities.get(1), entities.get(0)));

        assertThat(entities).extracting(CollectionWarningEntity::warningIndex).containsExactly(0, 1);
        assertThat(mapped.warnings()).containsExactlyElementsOf(warnings);
        assertThat(mapped.nextCollectionDueAt()).isNull();
    }

    @Test
    void mapsFailedAttemptNullableFailureFieldsAndDueSnapshot() {
        UUID applianceId = UUID.randomUUID();
        CollectionFailure failure =
                new CollectionFailure(CollectionFailureCategory.RATE_LIMITED, null, null);
        CollectionAttempt attempt = new CollectionAttempt(
                UUID.randomUUID(), applianceId, CollectionTrigger.MANUAL, CollectionOutcome.FAILED,
                START, COMPLETE, 0, List.of(), failure, null);
        CompletedCollection collection = new CompletedCollection(attempt, List.of());

        CollectionAttempt mapped =
                mapper.toDomain(mapper.toAttemptEntity(collection), List.of());

        assertThat(mapped.failure()).isEqualTo(failure);
        assertThat(mapped.failure().message()).isNull();
        assertThat(mapped.failure().retryAfterSeconds()).isNull();
        assertThat(mapped.nextCollectionDueAt()).isNull();
    }
}
