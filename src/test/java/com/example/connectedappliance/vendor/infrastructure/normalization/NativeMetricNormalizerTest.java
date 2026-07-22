package com.example.connectedappliance.vendor.infrastructure.normalization;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.connectedappliance.metrics.application.port.out.VendorMetricBatch;
import com.example.connectedappliance.metrics.application.port.out.VendorMetricWarningCode;
import com.example.connectedappliance.shared.metric.CanonicalMetric;
import com.example.connectedappliance.shared.metric.CanonicalUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NativeMetricNormalizerTest {

    private static final String TEMPERATURE_NAME = "temperature_f";
    private static final String TEMPERATURE_UNIT = "fahrenheit";
    private static final String POWER_NAME = "power_kw";
    private static final String POWER_UNIT = "kilowatts";

    private static final Map<String, NativeMetricMapping> MAPPINGS = Map.of(
            TEMPERATURE_NAME,
            new NativeMetricMapping(
                    TEMPERATURE_UNIT,
                    CanonicalMetric.TEMPERATURE,
                    VendorUnitConversions::fahrenheitToCelsius),
            POWER_NAME,
            new NativeMetricMapping(
                    POWER_UNIT,
                    CanonicalMetric.POWER,
                    VendorUnitConversions::kilowattsToWatts));

    private final NativeMetricNormalizer normalizer = new NativeMetricNormalizer();

    @Test
    void normalizesDistinctNativeNamesAndUnitsInInputOrder() {
        VendorMetricBatch batch = normalizer.normalize(
                List.of(
                        new NativeMetricReading(TEMPERATURE_NAME, TEMPERATURE_UNIT, "71.600000"),
                        new NativeMetricReading(POWER_NAME, POWER_UNIT, "0.150000")),
                MAPPINGS);

        assertEquals(2, batch.readings().size());
        assertEquals(CanonicalMetric.TEMPERATURE, batch.readings().get(0).metric());
        assertEquals(CanonicalUnit.CELSIUS, batch.readings().get(0).unit());
        assertEquals(new BigDecimal("22.000000"), batch.readings().get(0).value());
        assertEquals(CanonicalMetric.POWER, batch.readings().get(1).metric());
        assertEquals(CanonicalUnit.WATT, batch.readings().get(1).unit());
        assertEquals(new BigDecimal("150.000000"), batch.readings().get(1).value());
        assertEquals(6, batch.readings().get(0).value().scale());
        assertEquals(6, batch.readings().get(1).value().scale());
        assertEquals(List.of(), batch.warnings());
    }

    @Test
    void unknownMetricIsOmittedWithSanitizedWarning() {
        VendorMetricBatch batch = normalizer.normalize(
                List.of(new NativeMetricReading("unknown", POWER_UNIT, "10")), MAPPINGS);

        assertEquals(List.of(), batch.readings());
        assertWarningCodes(batch, VendorMetricWarningCode.UNKNOWN_METRIC);
        assertFalse(batch.warnings().get(0).message().contains("unknown"));
    }

    @Test
    void malformedValueIsOmittedWithoutLeakingParserDetails() {
        String rawValue = "task14-sensitive-malformed-value";

        VendorMetricBatch batch = normalizer.normalize(
                List.of(new NativeMetricReading(POWER_NAME, POWER_UNIT, rawValue)), MAPPINGS);

        assertEquals(List.of(), batch.readings());
        assertWarningCodes(batch, VendorMetricWarningCode.MALFORMED_VALUE);
        assertFalse(batch.warnings().get(0).message().contains(rawValue));
        assertFalse(batch.warnings().get(0).message().contains("NumberFormatException"));
    }

    @Test
    void incompatibleUnitIsOmittedWithoutGuessingConversion() {
        VendorMetricBatch batch = normalizer.normalize(
                List.of(new NativeMetricReading(POWER_NAME, TEMPERATURE_UNIT, "10")), MAPPINGS);

        assertEquals(List.of(), batch.readings());
        assertWarningCodes(batch, VendorMetricWarningCode.INCOMPATIBLE_UNIT);
    }

    @Test
    void mixedResponseKeepsValidReadingsAndOrdersWarningsByNativeInput() {
        VendorMetricBatch batch = normalizer.normalize(
                List.of(
                        new NativeMetricReading("unknown", POWER_UNIT, "10"),
                        new NativeMetricReading(TEMPERATURE_NAME, TEMPERATURE_UNIT, "71.600000"),
                        new NativeMetricReading(POWER_NAME, POWER_UNIT, "not-a-number"),
                        new NativeMetricReading(POWER_NAME, TEMPERATURE_UNIT, "0.150000")),
                MAPPINGS);

        assertEquals(1, batch.readings().size());
        assertEquals(CanonicalMetric.TEMPERATURE, batch.readings().get(0).metric());
        assertEquals(new BigDecimal("22.000000"), batch.readings().get(0).value());
        assertWarningCodes(
                batch,
                VendorMetricWarningCode.UNKNOWN_METRIC,
                VendorMetricWarningCode.MALFORMED_VALUE,
                VendorMetricWarningCode.INCOMPATIBLE_UNIT);
        String warningText = batch.warnings().stream()
                .map(warning -> warning.message())
                .reduce("", String::concat);
        for (String nativeDetail : List.of(
                "unknown", "not-a-number", TEMPERATURE_UNIT, POWER_UNIT, "0.150000")) {
            assertFalse(warningText.contains(nativeDetail));
        }
    }

    @Test
    void canonicalOverflowIsRejectedRatherThanClamped() {
        assertThrows(
                IllegalArgumentException.class,
                () -> normalizer.normalize(
                        List.of(new NativeMetricReading(
                                POWER_NAME, POWER_UNIT, "100000000000000")),
                        MAPPINGS));
    }

    @Test
    void rejectsNullCollectionsAndElements() {
        assertThrows(NullPointerException.class, () -> normalizer.normalize(null, MAPPINGS));
        assertThrows(NullPointerException.class, () -> normalizer.normalize(List.of(), null));
        assertThrows(
                NullPointerException.class,
                () -> normalizer.normalize(java.util.Arrays.asList((NativeMetricReading) null), MAPPINGS));
    }

    private void assertWarningCodes(
            VendorMetricBatch batch, VendorMetricWarningCode... expectedCodes) {
        assertEquals(
                List.of(expectedCodes),
                batch.warnings().stream().map(warning -> warning.code()).toList());
    }
}
