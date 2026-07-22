package com.example.connectedappliance.appliance.api.error;

import java.net.URI;

import org.springframework.http.HttpStatus;

import com.example.connectedappliance.shared.error.ApiProblemDefinition;

/** Approved sanitized Appliance-specific HTTP problem definitions. */
public final class ApplianceApiProblems {

    public static final ApiProblemDefinition APPLIANCE_NOT_FOUND = definition(
            HttpStatus.NOT_FOUND,
            "APPLIANCE_NOT_FOUND",
            "Appliance not found",
            "No appliance exists with the supplied identifier.");

    public static final ApiProblemDefinition DUPLICATE_APPLIANCE = definition(
            HttpStatus.CONFLICT,
            "DUPLICATE_APPLIANCE",
            "Duplicate appliance",
            "An appliance with the supplied vendor key and external reference already exists.");

    public static final ApiProblemDefinition UNSUPPORTED_VENDOR = definition(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "UNSUPPORTED_VENDOR",
            "Unsupported vendor",
            "The supplied vendor key is not supported.");

    private ApplianceApiProblems() {
    }

    private static ApiProblemDefinition definition(
            HttpStatus status,
            String code,
            String title,
            String detail) {
        String typeName = code.toLowerCase().replace('_', '-');
        return new ApiProblemDefinition(
                status,
                URI.create("urn:connected-appliance-platform:problem:" + typeName),
                title,
                detail,
                code);
    }
}
