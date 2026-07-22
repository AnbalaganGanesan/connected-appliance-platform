package com.example.connectedappliance.metrics.application.port.out;

/** Stable failure categories exposed by the vendor metric-source contract. */
public enum VendorFailureCategory {
    TIMEOUT,
    RATE_LIMITED,
    INVALID_DATA,
    TRANSIENT,
    UNEXPECTED
}
