package com.example.connectedappliance.appliance.application.port.out;

import java.util.List;
import java.util.Objects;

import com.example.connectedappliance.appliance.domain.Appliance;

/** Persistence-neutral Appliance page result. */
public record AppliancePage(
        List<Appliance> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public AppliancePage {
        items = List.copyOf(Objects.requireNonNull(items, "items must not be null"));
        if (page < 0) {
            throw new IllegalArgumentException("page must not be negative");
        }
        if (size <= 0) {
            throw new IllegalArgumentException("size must be positive");
        }
        if (totalElements < 0) {
            throw new IllegalArgumentException("totalElements must not be negative");
        }
        if (totalPages < 0) {
            throw new IllegalArgumentException("totalPages must not be negative");
        }
    }
}
