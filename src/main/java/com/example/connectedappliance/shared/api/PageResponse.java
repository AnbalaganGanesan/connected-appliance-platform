package com.example.connectedappliance.shared.api;

import java.util.List;
import java.util.Objects;

/** Public, persistence-neutral representation of one zero-based result page. */
public record PageResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public PageResponse {
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
