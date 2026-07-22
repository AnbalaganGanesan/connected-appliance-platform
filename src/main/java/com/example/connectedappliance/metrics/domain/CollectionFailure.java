package com.example.connectedappliance.metrics.domain;

import java.util.Objects;

/** Immutable, sanitized failure information for a failed collection attempt. */
public record CollectionFailure(
        CollectionFailureCategory category, String message, Integer retryAfterSeconds) {

    public CollectionFailure {
        Objects.requireNonNull(category, "category must not be null");
        if (message != null && message.length() > 500) {
            throw new IllegalArgumentException("message must not exceed 500 characters");
        }
        if (retryAfterSeconds != null && retryAfterSeconds <= 0) {
            throw new IllegalArgumentException("retryAfterSeconds must be positive");
        }
        if (retryAfterSeconds != null && category != CollectionFailureCategory.RATE_LIMITED) {
            throw new IllegalArgumentException("retryAfterSeconds is permitted only for rate limits");
        }
    }
}
