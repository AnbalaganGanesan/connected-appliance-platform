package com.example.connectedappliance.metrics.application.port.out;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.example.connectedappliance.metrics.domain.CollectionAttempt;
import com.example.connectedappliance.metrics.domain.CollectionOutcome;
import com.example.connectedappliance.metrics.domain.CollectionTrigger;
import com.example.connectedappliance.metrics.domain.MetricSample;
import com.example.connectedappliance.shared.metric.CanonicalMetric;
import com.example.connectedappliance.shared.metric.CanonicalUnit;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MetricsHistoryContractsTest {

    private static final Instant FROM = Instant.parse("2026-07-21T10:00:00Z");

    @Test
    void validatesPageAndAttemptQueryContracts() {
        HistoryPageRequest page = new HistoryPageRequest(0, 101);
        CollectionAttemptQuery query = new CollectionAttemptQuery(
                UUID.randomUUID(), Optional.of(CollectionTrigger.MANUAL), Optional.empty(), page);
        assertThat(query.pageRequest()).isEqualTo(page);
        assertThatThrownBy(() -> new HistoryPageRequest(-1, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HistoryPageRequest(0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CollectionAttemptQuery(
                        null, Optional.empty(), Optional.empty(), page))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CollectionAttemptQuery(
                        UUID.randomUUID(), null, Optional.empty(), page))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void validatesMetricRangeAndPreservesExactInstants() {
        UUID applianceId = UUID.randomUUID();
        Instant to = FROM.plusNanos(1_000);
        MetricSampleQuery query =
                new MetricSampleQuery(applianceId, FROM, to, new HistoryPageRequest(2, 3));
        assertThat(query.fromInclusive()).isSameAs(FROM);
        assertThat(query.toExclusive()).isSameAs(to);
        assertThatThrownBy(() -> new MetricSampleQuery(
                        applianceId, FROM, FROM, new HistoryPageRequest(0, 1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MetricSampleQuery(
                        applianceId, FROM, FROM.minusNanos(1), new HistoryPageRequest(0, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pageResultsAreImmutableAndAllowBeyondFinalMetadata() {
        CollectionAttempt attempt = new CollectionAttempt(
                UUID.randomUUID(), UUID.randomUUID(), CollectionTrigger.MANUAL,
                CollectionOutcome.SUCCESS, FROM, FROM, 1, List.of(), null, null);
        List<CollectionAttempt> attempts = new ArrayList<>(List.of(attempt));
        CollectionAttemptPage attemptPage = new CollectionAttemptPage(attempts, 5, 2, 1, 1);
        attempts.clear();
        assertThat(attemptPage.items()).containsExactly(attempt);
        assertThatThrownBy(() -> attemptPage.items().add(attempt))
                .isInstanceOf(UnsupportedOperationException.class);

        MetricSample sample = new MetricSample(
                UUID.randomUUID(), attempt.applianceId(), attempt.id(), CanonicalMetric.POWER,
                CanonicalUnit.WATT, BigDecimal.ONE, FROM, FROM);
        MetricSamplePage metricPage = new MetricSamplePage(List.of(sample), 5, 2, 1, 1);
        assertThat(metricPage.page()).isEqualTo(5);
        assertThatThrownBy(() -> metricPage.items().add(sample))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> new MetricSamplePage(List.of(), -1, 1, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
