package com.example.connectedappliance.shared.metric;

import java.math.BigDecimal;
import java.util.Objects;

/** Immutable, normalized, vendor-neutral metric reading. */
public record CanonicalMetricReading(
        CanonicalMetric metric, CanonicalUnit unit, BigDecimal value) {

    public CanonicalMetricReading {
        Objects.requireNonNull(metric, "metric must not be null");
        Objects.requireNonNull(unit, "unit must not be null");
        metric.validateUnit(unit);
        value = CanonicalNumericPolicy.normalize(value);
    }
}
