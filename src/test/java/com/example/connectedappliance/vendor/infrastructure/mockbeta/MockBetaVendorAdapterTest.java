package com.example.connectedappliance.vendor.infrastructure.mockbeta;

import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.connectedappliance.metrics.application.port.out.VendorFailureCategory;
import com.example.connectedappliance.metrics.application.port.out.VendorMetricBatch;
import com.example.connectedappliance.metrics.application.port.out.VendorMetricException;
import com.example.connectedappliance.metrics.application.port.out.VendorMetricWarningCode;
import com.example.connectedappliance.shared.metric.CanonicalMetric;
import com.example.connectedappliance.shared.metric.CanonicalMetricReading;
import com.example.connectedappliance.shared.metric.CanonicalUnit;
import com.example.connectedappliance.vendor.application.MockVendorScenarioExecutor;
import com.example.connectedappliance.vendor.application.VendorScenario;
import com.example.connectedappliance.vendor.application.port.VendorDelay;
import com.example.connectedappliance.vendor.infrastructure.normalization.NativeMetricNormalizer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MockBetaVendorAdapterTest {

    @Test
    void exposesStableMockBetaVendorKey() {
        assertEquals("mock-beta", fixture(VendorScenario.SUCCESS).adapter().vendorKey());
    }

    @Test
    void successConvertsApprovedValuesInDeterministicOrder() {
        MockBetaVendorAdapter adapter = fixture(VendorScenario.SUCCESS).adapter();

        VendorMetricBatch first = adapter.collect("device-1");
        VendorMetricBatch repeated = adapter.collect("device-1");
        VendorMetricBatch differentReference = adapter.collect("  Device/Bb-002  ");

        assertEquals(first, repeated);
        assertEquals(first, differentReference);
        assertEquals(2, first.readings().size());
        assertTrue(first.warnings().isEmpty());
        assertReading(
                first.readings().get(0),
                CanonicalMetric.TEMPERATURE,
                CanonicalUnit.CELSIUS,
                "22.000000");
        assertReading(
                first.readings().get(1),
                CanonicalMetric.POWER,
                CanonicalUnit.WATT,
                "150.000000");
    }

    @Test
    void partialReturnsConvertedTemperatureAndSanitizedWarning() {
        VendorMetricBatch batch = fixture(VendorScenario.PARTIAL).adapter().collect("device-1");

        assertEquals(1, batch.readings().size());
        assertReading(
                batch.readings().get(0),
                CanonicalMetric.TEMPERATURE,
                CanonicalUnit.CELSIUS,
                "22.000000");
        assertEquals(
                List.of(VendorMetricWarningCode.MALFORMED_VALUE),
                batch.warnings().stream().map(warning -> warning.code()).toList());
    }

    @Test
    void timeoutThrowsTypedSanitizedFailure() {
        assertFailure(VendorScenario.TIMEOUT, VendorFailureCategory.TIMEOUT, null, List.of());
    }

    @Test
    void rateLimitCarriesStablePositiveRetryAfter() {
        assertFailure(
                VendorScenario.RATE_LIMITED,
                VendorFailureCategory.RATE_LIMITED,
                30,
                List.of());
    }

    @Test
    void invalidDataCarriesAllOrderedSanitizedWarnings() {
        assertFailure(
                VendorScenario.INVALID_DATA,
                VendorFailureCategory.INVALID_DATA,
                null,
                List.of(
                        VendorMetricWarningCode.UNKNOWN_METRIC,
                        VendorMetricWarningCode.MALFORMED_VALUE,
                        VendorMetricWarningCode.INCOMPATIBLE_UNIT));
    }

    @Test
    void transientScenarioThrowsTypedSanitizedFailure() {
        assertFailure(VendorScenario.TRANSIENT, VendorFailureCategory.TRANSIENT, null, List.of());
    }

    @Test
    void unexpectedScenarioThrowsTypedSanitizedFailure() {
        assertFailure(
                VendorScenario.UNEXPECTED, VendorFailureCategory.UNEXPECTED, null, List.of());
    }

    @Test
    void configuredDelayIsInvokedExactlyOnceBeforeOutcome() {
        Duration delay = Duration.ofMillis(325);
        AdapterFixture fixture = fixture(VendorScenario.PARTIAL, delay);

        fixture.adapter().collect("device-1");

        assertEquals(1, fixture.delay().invocationCount());
        assertEquals(delay, fixture.delay().lastDelay());
    }

    @Test
    void rejectsNullAndBlankExternalReferencesWithoutInvokingDelay() {
        AdapterFixture fixture = fixture(VendorScenario.SUCCESS);

        assertThrows(NullPointerException.class, () -> fixture.adapter().collect(null));
        assertThrows(IllegalArgumentException.class, () -> fixture.adapter().collect(""));
        assertThrows(IllegalArgumentException.class, () -> fixture.adapter().collect("   "));
        assertEquals(0, fixture.delay().invocationCount());
    }

    @Test
    void returnedCollectionsAreImmutable() {
        VendorMetricBatch success = fixture(VendorScenario.SUCCESS).adapter().collect("device-1");
        VendorMetricBatch partial = fixture(VendorScenario.PARTIAL).adapter().collect("device-1");

        assertThrows(UnsupportedOperationException.class, () -> success.readings().clear());
        assertThrows(UnsupportedOperationException.class, () -> partial.warnings().clear());
    }

    @Test
    void nativeSnapshotRemainsPrivateAndUsesBigDecimalValues() {
        Class<?> nativeSnapshot = Arrays.stream(MockBetaVendorAdapter.class.getDeclaredClasses())
                .filter(type -> type.getSimpleName().equals("NativeSnapshot"))
                .findFirst()
                .orElseThrow();

        assertTrue(Modifier.isPrivate(nativeSnapshot.getModifiers()));
        assertTrue(nativeSnapshot.isRecord());
        assertArrayEquals(
                new String[] {"temperature_f", "power_kw"},
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

    private void assertFailure(
            VendorScenario scenario,
            VendorFailureCategory expectedCategory,
            Integer expectedRetryAfter,
            List<VendorMetricWarningCode> expectedWarnings) {
        VendorMetricException failure = assertThrows(
                VendorMetricException.class,
                () -> fixture(scenario).adapter().collect("opaque-reference"));

        assertEquals(expectedCategory, failure.category());
        assertEquals(expectedRetryAfter, failure.retryAfterSeconds());
        assertEquals(
                expectedWarnings,
                failure.warnings().stream().map(warning -> warning.code()).toList());
        assertEquals(expectedFailureMessage(expectedCategory), failure.getMessage());
        assertFalse(failure.getMessage().contains("opaque-reference"));
        assertNull(failure.getCause());
    }

    private String expectedFailureMessage(VendorFailureCategory category) {
        return switch (category) {
            case TIMEOUT -> "The vendor request timed out.";
            case RATE_LIMITED -> "The vendor rate limit was reached.";
            case INVALID_DATA -> "The vendor returned no usable metric readings.";
            case TRANSIENT -> "The vendor request failed temporarily.";
            case UNEXPECTED -> "The vendor request failed unexpectedly.";
        };
    }

    private AdapterFixture fixture(VendorScenario scenario) {
        return fixture(scenario, Duration.ZERO);
    }

    private AdapterFixture fixture(VendorScenario scenario, Duration delay) {
        RecordingDelay recordingDelay = new RecordingDelay();
        return new AdapterFixture(
                new MockBetaVendorAdapter(
                        new MockBetaProperties(scenario, delay),
                        new MockVendorScenarioExecutor(recordingDelay),
                        new NativeMetricNormalizer()),
                recordingDelay);
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

    private record AdapterFixture(MockBetaVendorAdapter adapter, RecordingDelay delay) {}

    private static final class RecordingDelay implements VendorDelay {

        private int invocationCount;
        private Duration lastDelay;

        @Override
        public void pause(Duration delay) {
            invocationCount++;
            lastDelay = delay;
        }

        private int invocationCount() {
            return invocationCount;
        }

        private Duration lastDelay() {
            return lastDelay;
        }
    }
}
