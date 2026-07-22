package com.example.connectedappliance.appliance.application.port.out;

/** Persistence-neutral, zero-based Appliance page request. */
public record AppliancePageRequest(int page, int size) {

    public AppliancePageRequest {
        if (page < 0) {
            throw new IllegalArgumentException("page must not be negative");
        }
        if (size <= 0) {
            throw new IllegalArgumentException("size must be positive");
        }
    }
}
