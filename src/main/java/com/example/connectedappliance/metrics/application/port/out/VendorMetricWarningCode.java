package com.example.connectedappliance.metrics.application.port.out;

/** Stable warning codes produced while normalizing vendor-native readings. */
public enum VendorMetricWarningCode {
    UNKNOWN_METRIC("A vendor metric was ignored because its name is unsupported."),
    MALFORMED_VALUE("A vendor metric was ignored because its value is malformed."),
    INCOMPATIBLE_UNIT("A vendor metric was ignored because its unit is incompatible.");

    private final String sanitizedMessage;

    VendorMetricWarningCode(String sanitizedMessage) {
        this.sanitizedMessage = sanitizedMessage;
    }

    public String sanitizedMessage() {
        return sanitizedMessage;
    }
}
