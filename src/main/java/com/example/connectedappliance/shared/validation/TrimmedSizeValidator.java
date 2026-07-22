package com.example.connectedappliance.shared.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Implements {@link TrimmedSize} without changing the supplied value. */
public final class TrimmedSizeValidator implements ConstraintValidator<TrimmedSize, String> {

    private int minimum;
    private int maximum;

    @Override
    public void initialize(TrimmedSize constraint) {
        minimum = constraint.min();
        maximum = constraint.max();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        int trimmedLength = value.strip().length();
        return trimmedLength >= minimum && trimmedLength <= maximum;
    }
}
