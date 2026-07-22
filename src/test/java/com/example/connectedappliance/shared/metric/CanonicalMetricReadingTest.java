package com.example.connectedappliance.shared.metric;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CanonicalMetricReadingTest {

    @Test
    void acceptsOnlyApprovedMetricUnitPairs() {
        CanonicalMetricReading temperature = reading(
                CanonicalMetric.TEMPERATURE, CanonicalUnit.CELSIUS, "21.5");
        CanonicalMetricReading power = reading(CanonicalMetric.POWER, CanonicalUnit.WATT, "125");

        assertEquals(CanonicalUnit.CELSIUS, temperature.unit());
        assertEquals(CanonicalUnit.WATT, power.unit());
        assertThrows(
                IllegalArgumentException.class,
                () -> reading(CanonicalMetric.TEMPERATURE, CanonicalUnit.WATT, "1"));
        assertThrows(
                IllegalArgumentException.class,
                () -> reading(CanonicalMetric.POWER, CanonicalUnit.CELSIUS, "1"));
    }

    @Test
    void rejectsNullMetricAndUnit() {
        assertThrows(
                NullPointerException.class,
                () -> new CanonicalMetricReading(null, CanonicalUnit.CELSIUS, BigDecimal.ONE));
        assertThrows(
                NullPointerException.class,
                () -> new CanonicalMetricReading(CanonicalMetric.TEMPERATURE, null, BigDecimal.ONE));
    }

    @Test
    void normalizesValuesToSixDecimalsUsingHalfUp() {
        assertNormalized("125.000000", "125");
        assertNormalized("21.500000", "21.5");
        assertNormalized("1.234567", "1.234567");
        assertNormalized("1.234567", "1.2345674");
        assertNormalized("1.234568", "1.2345675");
        assertNormalized("-1.234568", "-1.2345675");
    }

    @Test
    void acceptsZeroNegativeAndNumericBoundaries() {
        assertNormalized("0.000000", "0");
        assertNormalized("-42.000000", "-42");
        assertNormalized("99999999999999.999999", "99999999999999.999999");
        assertNormalized("-99999999999999.999999", "-99999999999999.999999");
    }

    @Test
    void rejectsIntegerOverflowAndRoundingCarry() {
        assertThrows(
                IllegalArgumentException.class,
                () -> reading(CanonicalMetric.POWER, CanonicalUnit.WATT, "100000000000000"));
        assertThrows(
                IllegalArgumentException.class,
                () -> reading(
                        CanonicalMetric.POWER,
                        CanonicalUnit.WATT,
                        "99999999999999.9999995"));
    }

    @Test
    void rejectsNullValue() {
        assertThrows(
                NullPointerException.class,
                () -> new CanonicalMetricReading(
                        CanonicalMetric.POWER, CanonicalUnit.WATT, null));
    }

    private void assertNormalized(String expected, String input) {
        BigDecimal actual = reading(CanonicalMetric.POWER, CanonicalUnit.WATT, input).value();

        assertEquals(new BigDecimal(expected), actual);
        assertEquals(CanonicalNumericPolicy.SCALE, actual.scale());
    }

    private CanonicalMetricReading reading(
            CanonicalMetric metric, CanonicalUnit unit, String value) {
        return new CanonicalMetricReading(metric, unit, new BigDecimal(value));
    }
}
