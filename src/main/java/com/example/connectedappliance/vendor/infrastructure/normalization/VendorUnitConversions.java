package com.example.connectedappliance.vendor.infrastructure.normalization;

import java.math.BigDecimal;

import com.example.connectedappliance.shared.metric.CanonicalNumericPolicy;

/** Exact decimal unit conversions used by Vendor-internal normalization rules. */
public final class VendorUnitConversions {

    private static final BigDecimal THIRTY_TWO = new BigDecimal("32");
    private static final BigDecimal FIVE = new BigDecimal("5");
    private static final BigDecimal NINE = new BigDecimal("9");
    private static final BigDecimal ONE_THOUSAND = new BigDecimal("1000");

    private VendorUnitConversions() {}

    public static BigDecimal fahrenheitToCelsius(BigDecimal fahrenheit) {
        BigDecimal converted = fahrenheit
                .subtract(THIRTY_TWO)
                .multiply(FIVE)
                .divide(
                        NINE,
                        CanonicalNumericPolicy.SCALE,
                        CanonicalNumericPolicy.ROUNDING_MODE);
        return CanonicalNumericPolicy.normalize(converted);
    }

    public static BigDecimal kilowattsToWatts(BigDecimal kilowatts) {
        return CanonicalNumericPolicy.normalize(kilowatts.multiply(ONE_THOUSAND));
    }

    public static BigDecimal preserveCanonicalUnit(BigDecimal value) {
        return CanonicalNumericPolicy.normalize(value);
    }
}
