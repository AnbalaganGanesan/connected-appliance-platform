package com.example.connectedappliance.metrics.infrastructure.execution;

import java.time.Duration;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Immutable validated configuration for Task 17 collection-control primitives. */
@Validated
@ConfigurationProperties(prefix = "app.collection")
public record CollectionControlProperties(
        @NotNull Duration vendorTimeout,
        @Min(1) int executorSize,
        @Min(1) int executorQueueCapacity,
        @NotNull Duration backoffCap) {

    public CollectionControlProperties {
        validateWholePositiveSeconds(vendorTimeout, "vendorTimeout");
        if (executorSize < 1) {
            throw new IllegalArgumentException("executorSize must be at least 1");
        }
        if (executorQueueCapacity < 1) {
            throw new IllegalArgumentException("executorQueueCapacity must be at least 1");
        }
        validateWholePositiveSeconds(backoffCap, "backoffCap");
    }

    private static void validateWholePositiveSeconds(Duration value, String name) {
        if (value != null && (value.isNegative() || value.isZero() || value.getNano() != 0)) {
            throw new IllegalArgumentException(
                    name + " must be a positive whole-second duration");
        }
    }
}
