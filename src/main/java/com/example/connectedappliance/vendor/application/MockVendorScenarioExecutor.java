package com.example.connectedappliance.vendor.application;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import com.example.connectedappliance.metrics.application.port.out.VendorFailureCategory;
import com.example.connectedappliance.metrics.application.port.out.VendorMetricBatch;
import com.example.connectedappliance.metrics.application.port.out.VendorMetricException;
import com.example.connectedappliance.vendor.application.port.VendorDelay;

/** Applies delay and deterministic scenario outcomes consistently across mock adapters. */
@Component
public final class MockVendorScenarioExecutor {

    public static final int RATE_LIMIT_RETRY_AFTER_SECONDS = 30;

    private static final String TIMEOUT_MESSAGE = "The vendor request timed out.";
    private static final String RATE_LIMITED_MESSAGE = "The vendor rate limit was reached.";
    private static final String INVALID_DATA_MESSAGE =
            "The vendor returned no usable metric readings.";
    private static final String TRANSIENT_MESSAGE = "The vendor request failed temporarily.";
    private static final String UNEXPECTED_MESSAGE = "The vendor request failed unexpectedly.";

    private final VendorDelay vendorDelay;

    public MockVendorScenarioExecutor(VendorDelay vendorDelay) {
        this.vendorDelay = Objects.requireNonNull(vendorDelay, "vendorDelay must not be null");
    }

    public VendorMetricBatch execute(
            VendorScenario scenario,
            Duration delay,
            Supplier<VendorMetricBatch> successBatch,
            Supplier<VendorMetricBatch> partialBatch,
            Supplier<VendorMetricBatch> invalidDataBatch) {
        Objects.requireNonNull(scenario, "scenario must not be null");
        Objects.requireNonNull(delay, "delay must not be null");
        Objects.requireNonNull(successBatch, "successBatch must not be null");
        Objects.requireNonNull(partialBatch, "partialBatch must not be null");
        Objects.requireNonNull(invalidDataBatch, "invalidDataBatch must not be null");

        vendorDelay.pause(delay);

        return switch (scenario) {
            case SUCCESS -> successBatch.get();
            case PARTIAL -> partialBatch.get();
            case TIMEOUT -> throw failure(VendorFailureCategory.TIMEOUT, TIMEOUT_MESSAGE, null);
            case RATE_LIMITED -> throw failure(
                    VendorFailureCategory.RATE_LIMITED,
                    RATE_LIMITED_MESSAGE,
                    RATE_LIMIT_RETRY_AFTER_SECONDS);
            case INVALID_DATA -> throw invalidDataFailure(invalidDataBatch.get());
            case TRANSIENT -> throw failure(
                    VendorFailureCategory.TRANSIENT, TRANSIENT_MESSAGE, null);
            case UNEXPECTED -> throw failure(
                    VendorFailureCategory.UNEXPECTED, UNEXPECTED_MESSAGE, null);
        };
    }

    private VendorMetricException invalidDataFailure(VendorMetricBatch rejectedBatch) {
        if (!rejectedBatch.readings().isEmpty()) {
            throw new IllegalStateException("Invalid-data scenario produced usable readings.");
        }
        return new VendorMetricException(
                VendorFailureCategory.INVALID_DATA,
                INVALID_DATA_MESSAGE,
                null,
                rejectedBatch.warnings());
    }

    private VendorMetricException failure(
            VendorFailureCategory category, String message, Integer retryAfterSeconds) {
        return new VendorMetricException(category, message, retryAfterSeconds, List.of());
    }
}
