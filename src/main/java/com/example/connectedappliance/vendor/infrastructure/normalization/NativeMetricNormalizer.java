package com.example.connectedappliance.vendor.infrastructure.normalization;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.example.connectedappliance.metrics.application.port.out.VendorMetricBatch;
import com.example.connectedappliance.metrics.application.port.out.VendorMetricWarning;
import com.example.connectedappliance.metrics.application.port.out.VendorMetricWarningCode;
import com.example.connectedappliance.shared.metric.CanonicalMetricReading;

/** Converts ordered vendor-native readings into canonical readings and sanitized warnings. */
@Component
public final class NativeMetricNormalizer {

    public VendorMetricBatch normalize(
            List<NativeMetricReading> nativeReadings,
            Map<String, NativeMetricMapping> mappingsByName) {
        Objects.requireNonNull(nativeReadings, "nativeReadings must not be null");
        Objects.requireNonNull(mappingsByName, "mappingsByName must not be null");

        List<CanonicalMetricReading> canonicalReadings = new ArrayList<>();
        List<VendorMetricWarning> warnings = new ArrayList<>();

        for (NativeMetricReading nativeReading : nativeReadings) {
            Objects.requireNonNull(nativeReading, "native reading must not be null");
            NativeMetricMapping mapping = mappingsByName.get(nativeReading.name());
            if (mapping == null) {
                warnings.add(VendorMetricWarning.forCode(VendorMetricWarningCode.UNKNOWN_METRIC));
                continue;
            }
            if (!mapping.expectedUnit().equals(nativeReading.unit())) {
                warnings.add(
                        VendorMetricWarning.forCode(VendorMetricWarningCode.INCOMPATIBLE_UNIT));
                continue;
            }

            BigDecimal nativeValue;
            try {
                nativeValue = new BigDecimal(nativeReading.rawValue());
            } catch (NumberFormatException malformedValue) {
                warnings.add(VendorMetricWarning.forCode(VendorMetricWarningCode.MALFORMED_VALUE));
                continue;
            }

            BigDecimal canonicalValue = mapping.converter().apply(nativeValue);
            canonicalReadings.add(new CanonicalMetricReading(
                    mapping.canonicalMetric(),
                    mapping.canonicalMetric().canonicalUnit(),
                    canonicalValue));
        }

        return new VendorMetricBatch(canonicalReadings, warnings);
    }
}
