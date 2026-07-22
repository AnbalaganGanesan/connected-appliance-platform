package com.example.connectedappliance.metrics.application.port.out;

import java.util.List;
import java.util.Objects;

import com.example.connectedappliance.shared.metric.CanonicalMetricReading;

/** Immutable batch of canonical readings and sanitized warnings returned by vendor integration. */
public record VendorMetricBatch(
        List<CanonicalMetricReading> readings, List<VendorMetricWarning> warnings) {

    public VendorMetricBatch {
        Objects.requireNonNull(readings, "readings must not be null");
        Objects.requireNonNull(warnings, "warnings must not be null");
        readings = List.copyOf(readings);
        warnings = List.copyOf(warnings);
    }

    public VendorMetricBatch(List<CanonicalMetricReading> readings) {
        this(readings, List.of());
    }
}
