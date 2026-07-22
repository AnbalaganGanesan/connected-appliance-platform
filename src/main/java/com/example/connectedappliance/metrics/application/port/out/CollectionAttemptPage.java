package com.example.connectedappliance.metrics.application.port.out;

import java.util.List;
import java.util.Objects;

import com.example.connectedappliance.metrics.domain.CollectionAttempt;

/** Immutable persistence-neutral page of collection attempts. */
public record CollectionAttemptPage(
        List<CollectionAttempt> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public CollectionAttemptPage {
        items = List.copyOf(Objects.requireNonNull(items, "items must not be null"));
        validateMetadata(page, size, totalElements, totalPages);
    }

    private static void validateMetadata(int page, int size, long totalElements, int totalPages) {
        if (page < 0 || size <= 0 || totalElements < 0 || totalPages < 0) {
            throw new IllegalArgumentException("page metadata must not be negative and size must be positive");
        }
    }
}
