package com.example.connectedappliance.vendor.infrastructure.mockalpha;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.example.connectedappliance.shared.metric.CanonicalMetric;
import com.example.connectedappliance.shared.metric.CanonicalMetricReading;
import com.example.connectedappliance.shared.metric.CanonicalUnit;
import com.example.connectedappliance.vendor.application.port.VendorAdapter;

/** Deterministic in-process adapter for the approved Mock Alpha vendor. */
@Component
public final class MockAlphaVendorAdapter implements VendorAdapter {

    public static final String VENDOR_KEY = "mock-alpha";

    private static final NativeSnapshot SNAPSHOT = new NativeSnapshot(
            new BigDecimal("21.500000"), new BigDecimal("125.000000"));

    @Override
    public String vendorKey() {
        return VENDOR_KEY;
    }

    @Override
    public List<CanonicalMetricReading> collect(String externalReference) {
        Objects.requireNonNull(externalReference, "externalReference must not be null");
        if (externalReference.isBlank()) {
            throw new IllegalArgumentException("externalReference must not be blank");
        }

        return List.of(
                new CanonicalMetricReading(
                        CanonicalMetric.TEMPERATURE,
                        CanonicalUnit.CELSIUS,
                        SNAPSHOT.temp_c()),
                new CanonicalMetricReading(
                        CanonicalMetric.POWER,
                        CanonicalUnit.WATT,
                        SNAPSHOT.power_w()));
    }

    private record NativeSnapshot(BigDecimal temp_c, BigDecimal power_w) {}
}
