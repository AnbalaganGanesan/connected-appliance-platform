package com.example.connectedappliance.metrics.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/** Immutable, ordered warning recorded for a completed collection attempt. */
public record CollectionWarning(String code, String message) {

    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]*$");

    public CollectionWarning {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(message, "message must not be null");
        if (code.length() > 64 || !CODE_PATTERN.matcher(code).matches()) {
            throw new IllegalArgumentException("code must use 1 to 64 upper-snake-case characters");
        }
        if (message.strip().isEmpty()) {
            throw new IllegalArgumentException("message must not be blank");
        }
        if (message.length() > 500) {
            throw new IllegalArgumentException("message must not exceed 500 characters");
        }
    }
}
