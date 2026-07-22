package com.example.connectedappliance.shared.error;

import java.util.List;
import java.util.Objects;

/** Carries sanitized validation items produced by explicit request-contract checks. */
public final class RequestValidationException extends ApiException {

    private final List<ValidationItem> errors;

    public RequestValidationException(String field, String code) {
        this(List.of(new ValidationItem(
                Objects.requireNonNull(field, "field must not be null"),
                Objects.requireNonNull(code, "code must not be null"),
                ValidationCodeMapper.standardMessage(code))));
    }

    public RequestValidationException(List<ValidationItem> errors) {
        super(CommonApiProblems.VALIDATION_ERROR);
        this.errors = List.copyOf(Objects.requireNonNull(errors, "errors must not be null"));
    }

    public List<ValidationItem> errors() {
        return errors;
    }
}
