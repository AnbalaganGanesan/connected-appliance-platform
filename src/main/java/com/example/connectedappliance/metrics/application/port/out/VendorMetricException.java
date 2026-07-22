package com.example.connectedappliance.metrics.application.port.out;

import java.util.List;
import java.util.Objects;

/** Typed, sanitized vendor failure consumable without importing Vendor infrastructure. */
public final class VendorMetricException extends RuntimeException {

    private static final int MAX_MESSAGE_LENGTH = 500;

    private final VendorFailureCategory category;
    private final Integer retryAfterSeconds;
    private final List<VendorMetricWarning> warnings;

    public VendorMetricException(
            VendorFailureCategory category,
            String message,
            Integer retryAfterSeconds,
            List<VendorMetricWarning> warnings) {
        super(validateMessage(message));
        this.category = Objects.requireNonNull(category, "failure category must not be null");
        validateRetryAfter(category, retryAfterSeconds);
        this.retryAfterSeconds = retryAfterSeconds;
        this.warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings must not be null"));
    }

    public VendorFailureCategory category() {
        return category;
    }

    public Integer retryAfterSeconds() {
        return retryAfterSeconds;
    }

    public List<VendorMetricWarning> warnings() {
        return warnings;
    }

    private static String validateMessage(String message) {
        Objects.requireNonNull(message, "failure message must not be null");
        if (message.isBlank()) {
            throw new IllegalArgumentException("failure message must not be blank");
        }
        if (message.length() > MAX_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("failure message must not exceed 500 characters");
        }
        return message;
    }

    private static void validateRetryAfter(
            VendorFailureCategory category, Integer retryAfterSeconds) {
        if (retryAfterSeconds != null && retryAfterSeconds <= 0) {
            throw new IllegalArgumentException("retry-after seconds must be positive");
        }
        if (retryAfterSeconds != null && category != VendorFailureCategory.RATE_LIMITED) {
            throw new IllegalArgumentException("retry-after is permitted only for rate limits");
        }
    }
}
