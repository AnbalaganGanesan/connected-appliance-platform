package com.example.connectedappliance.shared.error;

import java.util.Set;

import org.springframework.stereotype.Component;

@Component
public class ValidationCodeMapper {

    private static final Set<String> REQUIRED_CODES = Set.of(
            "NotBlank", "NotEmpty", "NotNull");
    private static final Set<String> RANGE_CODES = Set.of(
            "DecimalMax", "DecimalMin", "Max", "Min", "Negative", "NegativeOrZero",
            "Positive", "PositiveOrZero");
    private static final Set<String> LENGTH_CODES = Set.of("Size");
    private static final Set<String> FORMAT_CODES = Set.of(
            "Email", "Pattern", "typeMismatch");

    public String map(String sourceCode) {
        if (sourceCode == null) {
            return "INVALID_VALUE";
        }
        if (REQUIRED_CODES.contains(sourceCode)) {
            return "REQUIRED";
        }
        if (RANGE_CODES.contains(sourceCode)) {
            return "OUT_OF_RANGE";
        }
        if (LENGTH_CODES.contains(sourceCode)) {
            return "INVALID_LENGTH";
        }
        if (FORMAT_CODES.contains(sourceCode)) {
            return "INVALID_FORMAT";
        }
        return "INVALID_VALUE";
    }

    public String message(String publicCode) {
        return switch (publicCode) {
            case "REQUIRED" -> "is required";
            case "OUT_OF_RANGE" -> "must be within the allowed range";
            case "INVALID_LENGTH" -> "has an invalid length";
            case "INVALID_FORMAT" -> "has an invalid format";
            case "UNKNOWN_FIELD" -> "is not recognized";
            default -> "is invalid";
        };
    }
}
