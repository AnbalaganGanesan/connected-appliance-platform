package com.example.connectedappliance.metrics.api;

import java.util.Objects;

/** Sanitized warning returned in persisted collection-attempt order. */
public record CollectionWarningResponse(String code, String message) {

    public CollectionWarningResponse {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(message, "message must not be null");
    }
}
