package com.example.connectedappliance.metrics.application.port.out;

import java.util.List;
import java.util.Objects;

import com.example.connectedappliance.shared.metric.CanonicalMetricReading;

/** Immutable batch of canonical readings returned by vendor integration. */
public record VendorMetricBatch(List<CanonicalMetricReading> readings) {

    public VendorMetricBatch {
        Objects.requireNonNull(readings, "readings must not be null");
        readings = List.copyOf(readings);
    }
}
