package com.example.connectedappliance.metrics.application.port.out;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.connectedappliance.shared.metric.CanonicalMetric;
import com.example.connectedappliance.shared.metric.CanonicalMetricReading;
import com.example.connectedappliance.shared.metric.CanonicalUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VendorMetricContractsTest {

    private static final CanonicalMetricReading READING = new CanonicalMetricReading(
            CanonicalMetric.TEMPERATURE, CanonicalUnit.CELSIUS, new BigDecimal("21.5"));

    @Test
    void requestRejectsNullOrBlankVendorKey() {
        assertThrows(
                NullPointerException.class,
                () -> new VendorMetricRequest(null, "device-1"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new VendorMetricRequest("", "device-1"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new VendorMetricRequest("   ", "device-1"));
    }

    @Test
    void requestRejectsNullOrBlankExternalReference() {
        assertThrows(
                NullPointerException.class,
                () -> new VendorMetricRequest("mock-alpha", null));
        assertThrows(
                IllegalArgumentException.class,
                () -> new VendorMetricRequest("mock-alpha", ""));
        assertThrows(
                IllegalArgumentException.class,
                () -> new VendorMetricRequest("mock-alpha", "   "));
    }

    @Test
    void requestPreservesOpaqueExternalReferenceUnchanged() {
        String opaqueReference = "  Device/Aa-001  ";

        VendorMetricRequest request = new VendorMetricRequest("mock-alpha", opaqueReference);

        assertEquals(opaqueReference, request.externalReference());
    }

    @Test
    void batchDefensivelyCopiesAndExposesImmutableReadings() {
        List<CanonicalMetricReading> source = new ArrayList<>();
        source.add(READING);
        VendorMetricBatch batch = new VendorMetricBatch(source);

        source.clear();

        assertEquals(List.of(READING), batch.readings());
        assertThrows(UnsupportedOperationException.class, () -> batch.readings().add(READING));
    }

    @Test
    void batchRejectsNullListAndNullReadings() {
        assertThrows(NullPointerException.class, () -> new VendorMetricBatch(null));
        assertThrows(
                NullPointerException.class,
                () -> new VendorMetricBatch(java.util.Arrays.asList(READING, null)));
    }

    @Test
    void contractsExposeCanonicalBigDecimalValuesOnly() {
        assertEquals(BigDecimal.class, CanonicalMetricReading.class.getRecordComponents()[2].getType());
        assertFalse(hasFloatingPointComponent(CanonicalMetricReading.class));
        assertFalse(hasFloatingPointComponent(VendorMetricRequest.class));
        assertFalse(hasFloatingPointComponent(VendorMetricBatch.class));
    }

    private boolean hasFloatingPointComponent(Class<?> recordType) {
        return java.util.Arrays.stream(recordType.getRecordComponents())
                .map(component -> component.getType())
                .anyMatch(type -> type == double.class
                        || type == Double.class
                        || type == float.class
                        || type == Float.class);
    }
}
