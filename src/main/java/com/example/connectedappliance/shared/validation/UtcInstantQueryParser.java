package com.example.connectedappliance.shared.validation;

import java.time.DateTimeException;
import java.time.Instant;

import com.example.connectedappliance.shared.error.RequestValidationException;
import org.springframework.stereotype.Component;

/** Parses required public query timestamps using the strict uppercase-{@code Z} UTC contract. */
@Component
public final class UtcInstantQueryParser {

    public Instant parseRequired(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new RequestValidationException(field, "REQUIRED");
        }
        if (!value.endsWith("Z")) {
            throw new RequestValidationException(field, "INVALID_FORMAT");
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeException exception) {
            throw new RequestValidationException(field, "INVALID_FORMAT");
        }
    }
}
