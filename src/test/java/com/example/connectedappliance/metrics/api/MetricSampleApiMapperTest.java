package com.example.connectedappliance.metrics.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.example.connectedappliance.metrics.domain.MetricSample;
import com.example.connectedappliance.shared.api.PageResponse;
import com.example.connectedappliance.shared.metric.CanonicalMetric;
import com.example.connectedappliance.shared.metric.CanonicalUnit;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MetricSampleApiMapperTest {

    private final MetricSampleApiMapper mapper = new MetricSampleApiMapper();

    @Test
    void mapsEveryFieldWithoutNumericOrTimestampConversion() {
        MetricSample sample = sample(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                new BigDecimal("-21.500000"));

        MetricSampleResponse response = mapper.toResponse(sample);

        assertThat(response.id()).isEqualTo(sample.id());
        assertThat(response.applianceId()).isEqualTo(sample.applianceId());
        assertThat(response.collectionAttemptId()).isEqualTo(sample.collectionAttemptId());
        assertThat(response.metricName()).isEqualTo(CanonicalMetric.TEMPERATURE);
        assertThat(response.unit()).isEqualTo(CanonicalUnit.CELSIUS);
        assertThat(response.value()).isSameAs(sample.value()).isEqualTo("-21.500000");
        assertThat(response.value().scale()).isEqualTo(6);
        assertThat(response.observedAt()).isSameAs(sample.observedAt());
        assertThat(response.ingestedAt()).isSameAs(sample.ingestedAt());
    }

    @Test
    void preservesOrderAndDuplicateContentWhenMappedIntoCommonPage() {
        MetricSample first = sample(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                new BigDecimal("21.500000"));
        MetricSample second = sample(
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                new BigDecimal("21.500000"));

        PageResponse<MetricSampleResponse> page = new PageResponse<>(
                List.of(mapper.toResponse(first), mapper.toResponse(second)), 5, 2, 2, 1);

        assertThat(page.items()).extracting(MetricSampleResponse::id)
                .containsExactly(first.id(), second.id());
        assertThat(page.items()).extracting(MetricSampleResponse::value)
                .containsExactly(new BigDecimal("21.500000"), new BigDecimal("21.500000"));
        assertThat(page.page()).isEqualTo(5);
        assertThat(page.size()).isEqualTo(2);
        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.totalPages()).isEqualTo(1);
    }

    @Test
    void mapsEmptyPageWithoutIntroducingSpringPaginationMetadata() {
        PageResponse<MetricSampleResponse> page = new PageResponse<>(List.of(), 3, 20, 0, 0);

        assertThat(page.items()).isEmpty();
        assertThat(page.page()).isEqualTo(3);
        assertThat(page.totalElements()).isZero();
        assertThat(page.totalPages()).isZero();
    }

    private MetricSample sample(UUID id, BigDecimal value) {
        return new MetricSample(
                id,
                UUID.fromString("10000000-0000-0000-0000-000000000001"),
                UUID.fromString("20000000-0000-0000-0000-000000000001"),
                CanonicalMetric.TEMPERATURE,
                CanonicalUnit.CELSIUS,
                value,
                Instant.parse("2026-07-21T10:00:00.123456Z"),
                Instant.parse("2026-07-21T10:00:01.123456Z"));
    }
}
