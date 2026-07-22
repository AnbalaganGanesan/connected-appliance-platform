package com.example.connectedappliance.vendor.application;

/** Deterministic scenarios supported by both in-process mock vendors. */
public enum VendorScenario {
    SUCCESS,
    PARTIAL,
    TIMEOUT,
    RATE_LIMITED,
    INVALID_DATA,
    TRANSIENT,
    UNEXPECTED
}
