package com.example.connectedappliance.shared.error;

import java.net.URI;

import org.springframework.http.HttpStatus;

public final class CommonApiProblems {

    public static final ApiProblemDefinition INVALID_CORRELATION_ID = definition(
            HttpStatus.BAD_REQUEST,
            "INVALID_CORRELATION_ID",
            "Invalid correlation ID",
            "X-Correlation-ID must contain 1 to 64 letters, digits, periods, underscores, or hyphens.");

    public static final ApiProblemDefinition MALFORMED_JSON = definition(
            HttpStatus.BAD_REQUEST,
            "MALFORMED_JSON",
            "Malformed JSON",
            "The request body contains malformed JSON.");

    public static final ApiProblemDefinition VALIDATION_ERROR = definition(
            HttpStatus.BAD_REQUEST,
            "VALIDATION_ERROR",
            "Request validation failed",
            "One or more request values are invalid.");

    public static final ApiProblemDefinition INTERNAL_ERROR = definition(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "INTERNAL_ERROR",
            "Internal server error",
            "The request could not be completed due to an internal error.");

    public static final ApiProblemDefinition NOT_FOUND = definition(
            HttpStatus.NOT_FOUND,
            "NOT_FOUND",
            "Resource not found",
            "The requested resource was not found.");

    private CommonApiProblems() {
    }

    private static ApiProblemDefinition definition(
            HttpStatus status,
            String code,
            String title,
            String detail) {
        String typeName = code.toLowerCase().replace('_', '-');
        URI type = URI.create("urn:connected-appliance-platform:problem:" + typeName);
        return new ApiProblemDefinition(status, type, title, detail, code);
    }
}
