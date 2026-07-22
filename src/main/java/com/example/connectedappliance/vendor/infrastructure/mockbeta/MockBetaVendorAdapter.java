package com.example.connectedappliance.vendor.infrastructure.mockbeta;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.example.connectedappliance.metrics.application.port.out.VendorMetricBatch;
import com.example.connectedappliance.shared.metric.CanonicalMetric;
import com.example.connectedappliance.vendor.application.MockVendorScenarioExecutor;
import com.example.connectedappliance.vendor.application.port.VendorAdapter;
import com.example.connectedappliance.vendor.infrastructure.normalization.NativeMetricMapping;
import com.example.connectedappliance.vendor.infrastructure.normalization.NativeMetricNormalizer;
import com.example.connectedappliance.vendor.infrastructure.normalization.NativeMetricReading;
import com.example.connectedappliance.vendor.infrastructure.normalization.VendorUnitConversions;

/** Deterministic in-process adapter for the approved Mock Beta vendor. */
@Component
public final class MockBetaVendorAdapter implements VendorAdapter {

    public static final String VENDOR_KEY = "mock-beta";

    private static final String TEMPERATURE_NAME = "temperature_f";
    private static final String TEMPERATURE_UNIT = "fahrenheit";
    private static final String POWER_NAME = "power_kw";
    private static final String POWER_UNIT = "kilowatts";

    private static final NativeSnapshot SNAPSHOT = new NativeSnapshot(
            new BigDecimal("71.600000"), new BigDecimal("0.150000"));

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

    private final MockBetaProperties properties;
    private final MockVendorScenarioExecutor scenarioExecutor;
    private final NativeMetricNormalizer normalizer;

    public MockBetaVendorAdapter(
            MockBetaProperties properties,
            MockVendorScenarioExecutor scenarioExecutor,
            NativeMetricNormalizer normalizer) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.scenarioExecutor =
                Objects.requireNonNull(scenarioExecutor, "scenarioExecutor must not be null");
        this.normalizer = Objects.requireNonNull(normalizer, "normalizer must not be null");
    }

    @Override
    public String vendorKey() {
        return VENDOR_KEY;
    }

    @Override
    public VendorMetricBatch collect(String externalReference) {
        validateExternalReference(externalReference);
        return scenarioExecutor.execute(
                properties.scenario(),
                properties.delay(),
                () -> normalizer.normalize(successReadings(), MAPPINGS),
                () -> normalizer.normalize(partialReadings(), MAPPINGS),
                () -> normalizer.normalize(invalidReadings(), MAPPINGS));
    }

    private List<NativeMetricReading> successReadings() {
        return List.of(
                nativeReading(TEMPERATURE_NAME, TEMPERATURE_UNIT, SNAPSHOT.temperature_f()),
                nativeReading(POWER_NAME, POWER_UNIT, SNAPSHOT.power_kw()));
    }

    private List<NativeMetricReading> partialReadings() {
        return List.of(
                nativeReading(TEMPERATURE_NAME, TEMPERATURE_UNIT, SNAPSHOT.temperature_f()),
                new NativeMetricReading(POWER_NAME, POWER_UNIT, "not-a-number"));
    }

    private List<NativeMetricReading> invalidReadings() {
        return List.of(
                new NativeMetricReading("unsupported_metric", POWER_UNIT, "10"),
                new NativeMetricReading(TEMPERATURE_NAME, TEMPERATURE_UNIT, "not-a-number"),
                new NativeMetricReading(POWER_NAME, "incompatible-unit", "10"));
    }

    private NativeMetricReading nativeReading(
            String name, String unit, BigDecimal nativeValue) {
        return new NativeMetricReading(name, unit, nativeValue.toPlainString());
    }

    private void validateExternalReference(String externalReference) {
        Objects.requireNonNull(externalReference, "externalReference must not be null");
        if (externalReference.isBlank()) {
            throw new IllegalArgumentException("externalReference must not be blank");
        }
    }

    private record NativeSnapshot(BigDecimal temperature_f, BigDecimal power_kw) {}
}
