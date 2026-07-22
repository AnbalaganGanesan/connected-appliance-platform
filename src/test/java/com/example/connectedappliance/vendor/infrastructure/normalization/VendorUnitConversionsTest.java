package com.example.connectedappliance.vendor.infrastructure.normalization;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VendorUnitConversionsTest {

    @Test
    void convertsApprovedFahrenheitValueExactly() {
        assertDecimal(
                "22.000000",
                VendorUnitConversions.fahrenheitToCelsius(new BigDecimal("71.600000")));
    }

    @Test
    void roundsNonTerminatingFahrenheitConversionHalfUpToSixDecimals() {
        assertDecimal(
                "21.111111",
                VendorUnitConversions.fahrenheitToCelsius(new BigDecimal("70.000000")));
        assertDecimal(
                "0.000001",
                VendorUnitConversions.fahrenheitToCelsius(new BigDecimal("32.0000009")));
    }

    @Test
    void convertsKilowattsToWattsForPositiveZeroAndNegativeValues() {
        assertDecimal(
                "150.000000",
                VendorUnitConversions.kilowattsToWatts(new BigDecimal("0.150000")));
        assertDecimal(
                "0.000000",
                VendorUnitConversions.kilowattsToWatts(BigDecimal.ZERO));
        assertDecimal(
                "-150.000000",
                VendorUnitConversions.kilowattsToWatts(new BigDecimal("-0.150000")));
    }

    @Test
    void rejectsConvertedValuesOutsideCanonicalNumericPolicy() {
        assertThrows(
                IllegalArgumentException.class,
                () -> VendorUnitConversions.kilowattsToWatts(
                        new BigDecimal("100000000000000")));
    }

    private void assertDecimal(String expected, BigDecimal actual) {
        assertEquals(new BigDecimal(expected), actual);
        assertEquals(6, actual.scale());
    }
}
