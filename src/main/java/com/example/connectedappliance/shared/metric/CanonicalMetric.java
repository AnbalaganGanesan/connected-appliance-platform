package com.example.connectedappliance.shared.metric;

import java.util.Objects;

/** Canonical metric names and their single approved canonical units. */
public enum CanonicalMetric {
    TEMPERATURE(CanonicalUnit.CELSIUS),
    POWER(CanonicalUnit.WATT);

    private final CanonicalUnit canonicalUnit;

    CanonicalMetric(CanonicalUnit canonicalUnit) {
        this.canonicalUnit = canonicalUnit;
    }

    public CanonicalUnit canonicalUnit() {
        return canonicalUnit;
    }

    public void validateUnit(CanonicalUnit unit) {
        Objects.requireNonNull(unit, "unit must not be null");
        if (canonicalUnit != unit) {
            throw new IllegalArgumentException("Unit is not valid for the canonical metric");
        }
    }
}
