package com.example.connectedappliance.metrics.application.port.out;

import java.util.Objects;

/** Persistence-neutral, sanitized warning for a rejected vendor-native reading. */
public record VendorMetricWarning(VendorMetricWarningCode code, String message) {

    public static final int MAX_MESSAGE_LENGTH = 500;

    public VendorMetricWarning {
        Objects.requireNonNull(code, "warning code must not be null");
        Objects.requireNonNull(message, "warning message must not be null");
        if (message.isBlank()) {
            throw new IllegalArgumentException("warning message must not be blank");
        }
        if (message.length() > MAX_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("warning message must not exceed 500 characters");
        }
    }

    public static VendorMetricWarning forCode(VendorMetricWarningCode code) {
        Objects.requireNonNull(code, "warning code must not be null");
        return new VendorMetricWarning(code, code.sanitizedMessage());
    }
}
