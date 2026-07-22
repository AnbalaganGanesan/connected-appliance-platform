package com.example.connectedappliance.metrics.application.port.out;

import java.util.List;
import java.util.Objects;

import com.example.connectedappliance.metrics.domain.MetricSample;

/** Immutable persistence-neutral page of normalized samples. */
public record MetricSamplePage(
        List<MetricSample> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public MetricSamplePage {
        items = List.copyOf(Objects.requireNonNull(items, "items must not be null"));
        if (page < 0 || size <= 0 || totalElements < 0 || totalPages < 0) {
            throw new IllegalArgumentException("page metadata must not be negative and size must be positive");
        }
    }
}
