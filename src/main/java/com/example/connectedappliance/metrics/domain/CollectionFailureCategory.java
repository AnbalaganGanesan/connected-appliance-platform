package com.example.connectedappliance.metrics.domain;

/** Stable, vendor-neutral failure categories persisted with failed attempts. */
public enum CollectionFailureCategory {
    TIMEOUT,
    RATE_LIMITED,
    INVALID_DATA,
    TRANSIENT,
    UNEXPECTED
}
