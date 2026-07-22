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

    public static final ApiProblemDefinition UNSUPPORTED_MEDIA_TYPE = definition(
            HttpStatus.UNSUPPORTED_MEDIA_TYPE,
            "UNSUPPORTED_MEDIA_TYPE",
            "Unsupported media type",
            "The request media type is not supported.");

    public static final ApiProblemDefinition NOT_ACCEPTABLE = definition(
            HttpStatus.NOT_ACCEPTABLE,
            "NOT_ACCEPTABLE",
            "Not acceptable",
            "The requested response media type is not supported.");

    public static final ApiProblemDefinition APPLIANCE_PAUSED = definition(
            HttpStatus.CONFLICT,
            "APPLIANCE_PAUSED",
            "Appliance is paused",
            "The appliance is paused and cannot start collection.");

    public static final ApiProblemDefinition COLLECTION_ALREADY_IN_PROGRESS = definition(
            HttpStatus.CONFLICT,
            "COLLECTION_ALREADY_IN_PROGRESS",
            "Collection already in progress",
            "A collection is already in progress for the appliance.");

    public static final ApiProblemDefinition SERVICE_UNAVAILABLE = definition(
            HttpStatus.SERVICE_UNAVAILABLE,
            "SERVICE_UNAVAILABLE",
            "Service unavailable",
            "The collection service is temporarily unavailable.");

    public static final ApiProblemDefinition INVALID_TIME_RANGE = definition(
            HttpStatus.BAD_REQUEST,
            "INVALID_TIME_RANGE",
            "Invalid time range",
            "The start of the time range must be before the end.");

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
