package com.example.connectedappliance.metrics.api;

import java.util.Objects;

import com.example.connectedappliance.metrics.domain.CollectionFailureCategory;

/** Sanitized failure snapshot for a persisted failed collection attempt. */
public record CollectionFailureResponse(
        CollectionFailureCategory category,
        String message,
        Integer retryAfterSeconds) {

    public CollectionFailureResponse {
        Objects.requireNonNull(category, "category must not be null");
    }
}
