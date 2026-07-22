package com.example.connectedappliance.metrics.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.example.connectedappliance.shared.metric.CanonicalMetric;
import com.example.connectedappliance.shared.metric.CanonicalUnit;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompletedCollectionTest {

    private static final Instant START = Instant.parse("2026-07-21T10:00:00Z");

    @Test
    void acceptsSuccessPartialAndFailedCollections() {
        UUID applianceId = UUID.randomUUID();
        CollectionAttempt success = attempt(applianceId, CollectionOutcome.SUCCESS, 1, List.of(), null);
        CollectionAttempt partial = attempt(
                applianceId,
                CollectionOutcome.PARTIAL_SUCCESS,
                1,
                List.of(new CollectionWarning("UNKNOWN_METRIC", "ignored")),
                null);
        CollectionAttempt failed = attempt(
                applianceId,
                CollectionOutcome.FAILED,
                0,
                List.of(),
                new CollectionFailure(CollectionFailureCategory.TIMEOUT, null, null));

        assertThat(new CompletedCollection(success, List.of(sample(success))).samples()).hasSize(1);
        assertThat(new CompletedCollection(partial, List.of(sample(partial))).samples()).hasSize(1);
        assertThat(new CompletedCollection(failed, List.of()).samples()).isEmpty();
    }

    @Test
    void rejectsCountApplianceAttemptAndDuplicateIdMismatches() {
        UUID applianceId = UUID.randomUUID();
        CollectionAttempt attempt = attempt(applianceId, CollectionOutcome.SUCCESS, 1, List.of(), null);
        MetricSample valid = sample(attempt);

        assertThatThrownBy(() -> new CompletedCollection(attempt, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CompletedCollection(
                        attempt,
                        List.of(new MetricSample(
                                valid.id(), UUID.randomUUID(), attempt.id(), valid.metricName(), valid.unit(),
                                valid.value(), valid.observedAt(), valid.ingestedAt()))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CompletedCollection(
                        attempt,
                        List.of(new MetricSample(
                                valid.id(), applianceId, UUID.randomUUID(), valid.metricName(), valid.unit(),
                                valid.value(), valid.observedAt(), valid.ingestedAt()))))
                .isInstanceOf(IllegalArgumentException.class);

        CollectionAttempt twoSampleAttempt =
                attempt(applianceId, CollectionOutcome.SUCCESS, 2, List.of(), null);
        MetricSample first = sample(twoSampleAttempt);
        MetricSample duplicate = new MetricSample(
                first.id(), applianceId, twoSampleAttempt.id(), CanonicalMetric.POWER,
                CanonicalUnit.WATT, BigDecimal.TEN, START, START);
        assertThatThrownBy(() -> new CompletedCollection(twoSampleAttempt, List.of(first, duplicate)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void defensivelyCopiesAndFreezesSamples() {
        UUID applianceId = UUID.randomUUID();
        CollectionAttempt attempt = attempt(applianceId, CollectionOutcome.SUCCESS, 1, List.of(), null);
        MetricSample sample = sample(attempt);
        List<MetricSample> source = new ArrayList<>(List.of(sample));
        CompletedCollection collection = new CompletedCollection(attempt, source);
        source.clear();

        assertThat(collection.samples()).containsExactly(sample);
        assertThatThrownBy(() -> collection.samples().add(sample))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private CollectionAttempt attempt(
            UUID applianceId,
            CollectionOutcome outcome,
            int count,
            List<CollectionWarning> warnings,
            CollectionFailure failure) {
        return new CollectionAttempt(
                UUID.randomUUID(), applianceId, CollectionTrigger.MANUAL, outcome,
                START, START.plusSeconds(1), count, warnings, failure, null);
    }

    private MetricSample sample(CollectionAttempt attempt) {
        return new MetricSample(
                UUID.randomUUID(), attempt.applianceId(), attempt.id(), CanonicalMetric.TEMPERATURE,
                CanonicalUnit.CELSIUS, new BigDecimal("21.5"), START, START);
    }
}
