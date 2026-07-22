package com.example.connectedappliance.shared.metric;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class CanonicalMetricCatalogTest {

    @Test
    void containsOnlyApprovedCanonicalMetrics() {
        assertArrayEquals(
                new String[] {"TEMPERATURE", "POWER"},
                java.util.Arrays.stream(CanonicalMetric.values()).map(Enum::name).toArray(String[]::new));
    }

    @Test
    void containsOnlyApprovedCanonicalUnits() {
        assertArrayEquals(
                new String[] {"CELSIUS", "WATT"},
                java.util.Arrays.stream(CanonicalUnit.values()).map(Enum::name).toArray(String[]::new));
    }
}
