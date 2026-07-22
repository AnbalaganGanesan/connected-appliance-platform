package com.example.connectedappliance.vendor.infrastructure.mockalpha;

import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.connectedappliance.shared.metric.CanonicalMetric;
import com.example.connectedappliance.shared.metric.CanonicalMetricReading;
import com.example.connectedappliance.shared.metric.CanonicalUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MockAlphaVendorAdapterTest {

    private final MockAlphaVendorAdapter adapter = new MockAlphaVendorAdapter();

    @Test
    void exposesStableMockAlphaVendorKey() {
        assertEquals("mock-alpha", adapter.vendorKey());
    }

    @Test
    void returnsApprovedCanonicalReadingsInDeterministicOrder() {
        List<CanonicalMetricReading> readings = adapter.collect("device-1");

        assertEquals(2, readings.size());
        assertReading(
                readings.get(0),
                CanonicalMetric.TEMPERATURE,
                CanonicalUnit.CELSIUS,
                "21.500000");
        assertReading(
                readings.get(1), CanonicalMetric.POWER, CanonicalUnit.WATT, "125.000000");
    }

    @Test
    void repeatedCallsAndDifferentReferencesReturnEqualResults() {
        List<CanonicalMetricReading> first = adapter.collect("device-1");
        List<CanonicalMetricReading> repeated = adapter.collect("device-1");
        List<CanonicalMetricReading> differentReference = adapter.collect("Device/Aa-002");

        assertEquals(first, repeated);
        assertEquals(first, differentReference);
    }

    @Test
    void rejectsNullAndBlankExternalReferences() {
        assertThrows(NullPointerException.class, () -> adapter.collect(null));
        assertThrows(IllegalArgumentException.class, () -> adapter.collect(""));
        assertThrows(IllegalArgumentException.class, () -> adapter.collect("   "));
    }

    @Test
    void returnedReadingsAreImmutable() {
        List<CanonicalMetricReading> readings = adapter.collect("device-1");

        assertThrows(UnsupportedOperationException.class, () -> readings.clear());
    }

    @Test
    void nativeSnapshotRemainsPrivateAndUsesBigDecimalValues() {
        Class<?> nativeSnapshot = Arrays.stream(MockAlphaVendorAdapter.class.getDeclaredClasses())
                .filter(type -> type.getSimpleName().equals("NativeSnapshot"))
                .findFirst()
                .orElseThrow();

        assertTrue(Modifier.isPrivate(nativeSnapshot.getModifiers()));
        assertTrue(nativeSnapshot.isRecord());
        assertArrayEquals(
                new String[] {"temp_c", "power_w"},
                Arrays.stream(nativeSnapshot.getRecordComponents())
                        .map(component -> component.getName())
                        .toArray(String[]::new));
        assertFalse(Arrays.stream(nativeSnapshot.getRecordComponents())
                .map(component -> component.getType())
                .anyMatch(type -> type == double.class
                        || type == Double.class
                        || type == float.class
                        || type == Float.class));
        assertTrue(Arrays.stream(nativeSnapshot.getRecordComponents())
                .allMatch(component -> component.getType() == BigDecimal.class));
    }

    private void assertReading(
            CanonicalMetricReading reading,
            CanonicalMetric metric,
            CanonicalUnit unit,
            String value) {
        assertEquals(metric, reading.metric());
        assertEquals(unit, reading.unit());
        assertEquals(new BigDecimal(value), reading.value());
        assertEquals(6, reading.value().scale());
    }
}
