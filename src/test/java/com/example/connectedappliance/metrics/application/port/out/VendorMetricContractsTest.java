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
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        List<VendorMetricWarning> warningSource = new ArrayList<>();
        warningSource.add(VendorMetricWarning.forCode(VendorMetricWarningCode.UNKNOWN_METRIC));
        VendorMetricBatch batch = new VendorMetricBatch(source, warningSource);

        source.clear();
        warningSource.clear();

        assertEquals(List.of(READING), batch.readings());
        assertEquals(
                List.of(VendorMetricWarning.forCode(VendorMetricWarningCode.UNKNOWN_METRIC)),
                batch.warnings());
        assertThrows(UnsupportedOperationException.class, () -> batch.readings().add(READING));
        assertThrows(
                UnsupportedOperationException.class,
                () -> batch.warnings().add(
                        VendorMetricWarning.forCode(VendorMetricWarningCode.MALFORMED_VALUE)));
    }

    @Test
    void batchRejectsNullListAndNullReadings() {
        assertThrows(NullPointerException.class, () -> new VendorMetricBatch(null));
        assertThrows(NullPointerException.class, () -> new VendorMetricBatch(List.of(), null));
        assertThrows(
                NullPointerException.class,
                () -> new VendorMetricBatch(java.util.Arrays.asList(READING, null)));
        assertThrows(
                NullPointerException.class,
                () -> new VendorMetricBatch(
                        List.of(),
                        java.util.Arrays.asList(
                                VendorMetricWarning.forCode(
                                        VendorMetricWarningCode.UNKNOWN_METRIC),
                                null)));
    }

    @Test
    void warningContractUsesExactCodesAndSanitizedBoundedMessages() {
        assertEquals(
                List.of("UNKNOWN_METRIC", "MALFORMED_VALUE", "INCOMPATIBLE_UNIT"),
                java.util.Arrays.stream(VendorMetricWarningCode.values())
                        .map(Enum::name)
                        .toList());

        VendorMetricWarning warning =
                VendorMetricWarning.forCode(VendorMetricWarningCode.MALFORMED_VALUE);

        assertEquals(VendorMetricWarningCode.MALFORMED_VALUE, warning.code());
        assertEquals(
                "A vendor metric was ignored because its value is malformed.",
                warning.message());
        assertThrows(NullPointerException.class, () -> new VendorMetricWarning(null, "message"));
        assertThrows(
                NullPointerException.class,
                () -> new VendorMetricWarning(VendorMetricWarningCode.UNKNOWN_METRIC, null));
        assertThrows(
                IllegalArgumentException.class,
                () -> new VendorMetricWarning(VendorMetricWarningCode.UNKNOWN_METRIC, "   "));
        assertThrows(
                IllegalArgumentException.class,
                () -> new VendorMetricWarning(
                        VendorMetricWarningCode.UNKNOWN_METRIC, "x".repeat(501)));
        assertTrue(java.util.Arrays.stream(VendorMetricWarningCode.values())
                .allMatch(code -> code.name().length() <= 64));
        assertEquals(
                List.of(
                        "A vendor metric was ignored because its name is unsupported.",
                        "A vendor metric was ignored because its value is malformed.",
                        "A vendor metric was ignored because its unit is incompatible."),
                java.util.Arrays.stream(VendorMetricWarningCode.values())
                        .map(VendorMetricWarningCode::sanitizedMessage)
                        .toList());
    }

    @Test
    void failureContractUsesExactCategoriesAndEnforcesRetryAfterRules() {
        assertEquals(
                List.of("TIMEOUT", "RATE_LIMITED", "INVALID_DATA", "TRANSIENT", "UNEXPECTED"),
                java.util.Arrays.stream(VendorFailureCategory.values())
                        .map(Enum::name)
                        .toList());

        List<VendorMetricWarning> warningSource = new ArrayList<>();
        warningSource.add(VendorMetricWarning.forCode(VendorMetricWarningCode.UNKNOWN_METRIC));
        VendorMetricException failure = new VendorMetricException(
                VendorFailureCategory.RATE_LIMITED,
                "The vendor rate limit was reached.",
                30,
                warningSource);
        warningSource.clear();

        assertEquals(VendorFailureCategory.RATE_LIMITED, failure.category());
        assertEquals(30, failure.retryAfterSeconds());
        assertEquals(1, failure.warnings().size());
        assertThrows(UnsupportedOperationException.class, () -> failure.warnings().clear());
        assertThrows(
                IllegalArgumentException.class,
                () -> new VendorMetricException(
                        VendorFailureCategory.TIMEOUT, "message", 30, List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new VendorMetricException(
                        VendorFailureCategory.RATE_LIMITED, "message", 0, List.of()));
        assertThrows(
                NullPointerException.class,
                () -> new VendorMetricException(
                        VendorFailureCategory.TIMEOUT, "message", null, null));
        assertThrows(
                NullPointerException.class,
                () -> new VendorMetricException(
                        VendorFailureCategory.INVALID_DATA,
                        "message",
                        null,
                        java.util.Arrays.asList(
                                VendorMetricWarning.forCode(
                                        VendorMetricWarningCode.UNKNOWN_METRIC),
                                null)));
        assertThrows(
                NullPointerException.class,
                () -> new VendorMetricException(null, "message", null, List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new VendorMetricException(
                        VendorFailureCategory.TIMEOUT, "  ", null, List.of()));
        assertTrue(failure.getCause() == null);
    }

    @Test
    void publicContractsContainOnlyCanonicalAndSanitizedFields() {
        assertEquals(
                List.of("readings", "warnings"),
                java.util.Arrays.stream(VendorMetricBatch.class.getRecordComponents())
                        .map(component -> component.getName())
                        .toList());
        assertEquals(
                List.of("code", "message"),
                java.util.Arrays.stream(VendorMetricWarning.class.getRecordComponents())
                        .map(component -> component.getName())
                        .toList());

        String publicShape = (VendorMetricBatch.class.getName()
                        + VendorMetricWarning.class.getName()
                        + VendorMetricException.class.getName()
                        + java.util.Arrays.toString(VendorMetricBatch.class.getRecordComponents())
                        + java.util.Arrays.toString(VendorMetricWarning.class.getRecordComponents()))
                .toLowerCase();
        for (String forbidden : List.of(
                "native", "payload", "externalreference", "credential", "vendorkey")) {
            assertFalse(publicShape.contains(forbidden));
        }
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
