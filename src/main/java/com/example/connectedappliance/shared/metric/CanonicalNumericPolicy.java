package com.example.connectedappliance.shared.metric;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** PostgreSQL {@code numeric(20,6)} normalization policy for canonical metric values. */
public final class CanonicalNumericPolicy {

    public static final int PRECISION = 20;
    public static final int SCALE = 6;
    public static final int MAX_INTEGER_DIGITS = PRECISION - SCALE;
    public static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    private CanonicalNumericPolicy() {}

    public static BigDecimal normalize(BigDecimal value) {
        Objects.requireNonNull(value, "value must not be null");

        BigDecimal normalized = value.setScale(SCALE, ROUNDING_MODE);
        int integerDigits = Math.max(0, normalized.precision() - normalized.scale());
        if (normalized.precision() > PRECISION || integerDigits > MAX_INTEGER_DIGITS) {
            throw new IllegalArgumentException("Canonical metric value exceeds numeric(20,6)");
        }

        return normalized;
    }
}
