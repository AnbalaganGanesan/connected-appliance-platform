package com.example.connectedappliance.vendor.infrastructure.normalization;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.function.UnaryOperator;

import com.example.connectedappliance.shared.metric.CanonicalMetric;

/** Exact Vendor-internal rule for one native metric name and unit. */
public record NativeMetricMapping(
        String expectedUnit,
        CanonicalMetric canonicalMetric,
        UnaryOperator<BigDecimal> converter) {

    public NativeMetricMapping {
        Objects.requireNonNull(expectedUnit, "expectedUnit must not be null");
        Objects.requireNonNull(canonicalMetric, "canonicalMetric must not be null");
        Objects.requireNonNull(converter, "converter must not be null");
    }
}
