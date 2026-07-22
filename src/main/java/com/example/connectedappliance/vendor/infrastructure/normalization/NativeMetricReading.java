package com.example.connectedappliance.vendor.infrastructure.normalization;

import java.util.Objects;

/** Immutable Vendor-internal reading before name, unit, and numeric normalization. */
public record NativeMetricReading(String name, String unit, String rawValue) {

    public NativeMetricReading {
        Objects.requireNonNull(name, "native name must not be null");
        Objects.requireNonNull(unit, "native unit must not be null");
        Objects.requireNonNull(rawValue, "native value must not be null");
    }
}
