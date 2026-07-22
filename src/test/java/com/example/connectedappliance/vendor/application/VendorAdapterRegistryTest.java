package com.example.connectedappliance.vendor.application;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.connectedappliance.metrics.application.port.out.VendorMetricBatch;
import com.example.connectedappliance.metrics.application.port.out.VendorMetricRequest;
import com.example.connectedappliance.shared.metric.CanonicalMetric;
import com.example.connectedappliance.shared.metric.CanonicalMetricReading;
import com.example.connectedappliance.shared.metric.CanonicalUnit;
import com.example.connectedappliance.vendor.application.port.VendorAdapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VendorAdapterRegistryTest {

    private static final CanonicalMetricReading READING = new CanonicalMetricReading(
            CanonicalMetric.TEMPERATURE, CanonicalUnit.CELSIUS, new BigDecimal("21.500000"));

    @Test
    void supportsOnlyExactRegisteredVendorKeys() {
        VendorAdapterRegistry registry = new VendorAdapterRegistry(
                List.of(new RecordingAdapter("mock-alpha", List.of(READING))));

        assertTrue(registry.isSupported("mock-alpha"));
        assertFalse(registry.isSupported("MOCK-ALPHA"));
        assertFalse(registry.isSupported(" mock-alpha "));
        assertFalse(registry.isSupported("unknown"));
        assertFalse(registry.isSupported(null));
        assertFalse(registry.isSupported(""));
        assertFalse(registry.isSupported("   "));
    }

    @Test
    void delegatesOnceAndPreservesExternalReference() {
        RecordingAdapter selected = new RecordingAdapter("mock-alpha", List.of(READING));
        RecordingAdapter other = new RecordingAdapter("other", List.of());
        VendorAdapterRegistry registry = new VendorAdapterRegistry(List.of(selected, other));
        String opaqueReference = "  Device/Aa-001  ";

        VendorMetricBatch result = registry.collect(
                new VendorMetricRequest("mock-alpha", opaqueReference));

        assertEquals(List.of(READING), result.readings());
        assertEquals(1, selected.invocationCount());
        assertEquals(opaqueReference, selected.lastExternalReference());
        assertEquals(0, other.invocationCount());
    }

    @Test
    void unsupportedCollectionFailsWithoutInvokingAnyAdapter() {
        RecordingAdapter adapter = new RecordingAdapter("mock-alpha", List.of(READING));
        VendorAdapterRegistry registry = new VendorAdapterRegistry(List.of(adapter));
        String opaqueReference = "sensitive-external-reference";

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> registry.collect(new VendorMetricRequest("unknown", opaqueReference)));

        assertEquals("Vendor adapter is not supported.", failure.getMessage());
        assertFalse(failure.getMessage().contains(opaqueReference));
        assertEquals(0, adapter.invocationCount());
    }

    @Test
    void duplicateAdapterKeysFailConstruction() {
        RecordingAdapter first = new RecordingAdapter("duplicate", List.of(READING));
        RecordingAdapter second = new RecordingAdapter("duplicate", List.of(READING));

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> new VendorAdapterRegistry(List.of(first, second)));

        assertEquals("Duplicate vendor adapter registration.", failure.getMessage());
        assertEquals(0, first.invocationCount());
        assertEquals(0, second.invocationCount());
    }

    @Test
    void nullAndBlankAdapterKeysFailConstruction() {
        IllegalStateException nullKeyFailure = assertThrows(
                IllegalStateException.class,
                () -> new VendorAdapterRegistry(List.of(new RecordingAdapter(null, List.of()))));
        IllegalStateException blankKeyFailure = assertThrows(
                IllegalStateException.class,
                () -> new VendorAdapterRegistry(List.of(new RecordingAdapter("   ", List.of()))));

        assertEquals("Vendor adapter registration is invalid.", nullKeyFailure.getMessage());
        assertEquals("Vendor adapter registration is invalid.", blankKeyFailure.getMessage());
    }

    private static final class RecordingAdapter implements VendorAdapter {

        private final String vendorKey;
        private final List<CanonicalMetricReading> readings;
        private int invocationCount;
        private String lastExternalReference;

        private RecordingAdapter(String vendorKey, List<CanonicalMetricReading> readings) {
            this.vendorKey = vendorKey;
            this.readings = readings;
        }

        @Override
        public String vendorKey() {
            return vendorKey;
        }

        @Override
        public List<CanonicalMetricReading> collect(String externalReference) {
            invocationCount++;
            lastExternalReference = externalReference;
            return readings;
        }

        private int invocationCount() {
            return invocationCount;
        }

        private String lastExternalReference() {
            return lastExternalReference;
        }
    }
}
