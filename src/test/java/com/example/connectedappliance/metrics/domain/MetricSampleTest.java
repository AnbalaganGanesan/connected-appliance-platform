package com.example.connectedappliance.metrics.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.example.connectedappliance.shared.metric.CanonicalMetric;
import com.example.connectedappliance.shared.metric.CanonicalUnit;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MetricSampleTest {

    private static final Instant TIME = Instant.parse("2026-07-21T10:00:00Z");

    @Test
    void acceptsCanonicalPairsAndNormalizesValuesToScaleSix() {
        MetricSample temperature = sample(
                CanonicalMetric.TEMPERATURE,
                CanonicalUnit.CELSIUS,
                new BigDecimal("21.2345675"),
                TIME,
                TIME.minusSeconds(10));
        MetricSample power = sample(
                CanonicalMetric.POWER,
                CanonicalUnit.WATT,
                new BigDecimal("125"),
                TIME,
                TIME);

        assertThat(temperature.value()).isEqualTo(new BigDecimal("21.234568"));
        assertThat(temperature.value().scale()).isEqualTo(6);
        assertThat(power.value()).isEqualTo(new BigDecimal("125.000000"));
    }

    @Test
    void acceptsNegativeValuesAndDoesNotImposeTimestampOrdering() {
        MetricSample sample = sample(
                CanonicalMetric.TEMPERATURE,
                CanonicalUnit.CELSIUS,
                new BigDecimal("-1.2345675"),
                TIME,
                TIME.minusSeconds(1));
        assertThat(sample.value()).isEqualTo(new BigDecimal("-1.234568"));
        assertThat(sample.ingestedAt()).isBefore(sample.observedAt());
    }

    @Test
    void rejectsInvalidPairOverflowAndNullFields() {
        assertThatThrownBy(() -> sample(
                        CanonicalMetric.TEMPERATURE,
                        CanonicalUnit.WATT,
                        BigDecimal.ONE,
                        TIME,
                        TIME))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> sample(
                        CanonicalMetric.POWER,
                        CanonicalUnit.WATT,
                        new BigDecimal("100000000000000.000000"),
                        TIME,
                        TIME))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> sample(
                        CanonicalMetric.POWER, CanonicalUnit.WATT, null, TIME, TIME))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new MetricSample(
                        null, UUID.randomUUID(), UUID.randomUUID(), CanonicalMetric.POWER,
                        CanonicalUnit.WATT, BigDecimal.ONE, TIME, TIME))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new MetricSample(
                        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null,
                        CanonicalUnit.WATT, BigDecimal.ONE, TIME, TIME))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new MetricSample(
                        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), CanonicalMetric.POWER,
                        CanonicalUnit.WATT, BigDecimal.ONE, null, TIME))
                .isInstanceOf(NullPointerException.class);
    }

    private MetricSample sample(
            CanonicalMetric metric,
            CanonicalUnit unit,
            BigDecimal value,
            Instant observedAt,
            Instant ingestedAt) {
        return new MetricSample(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                metric,
                unit,
                value,
                observedAt,
                ingestedAt);
    }
}
