package com.example.connectedappliance.metrics.application.control;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

import com.example.connectedappliance.metrics.application.port.out.VendorFailureCategory;
import com.example.connectedappliance.metrics.application.port.out.VendorMetricException;
import com.example.connectedappliance.metrics.domain.CollectionFailure;
import com.example.connectedappliance.metrics.domain.CollectionFailureCategory;
import com.example.connectedappliance.metrics.domain.CollectionWarning;

/** Converts typed and execution failures into sanitized Metrics values. */
public final class VendorFailureClassifier {

    public static final String TIMEOUT_MESSAGE = "The vendor request timed out.";
    public static final String TEMPORARY_MESSAGE = "The vendor request failed temporarily.";
    public static final String UNEXPECTED_MESSAGE = "The vendor request failed unexpectedly.";

    public ClassifiedVendorFailure classify(Throwable throwable) {
        Objects.requireNonNull(throwable, "throwable must not be null");
        if (throwable instanceof VendorMetricException vendorFailure) {
            return classifyVendorFailure(vendorFailure);
        }
        if (throwable instanceof TimeoutException) {
            return simpleFailure(CollectionFailureCategory.TIMEOUT, TIMEOUT_MESSAGE);
        }
        if (throwable instanceof InterruptedException) {
            return simpleFailure(CollectionFailureCategory.TRANSIENT, TEMPORARY_MESSAGE);
        }
        return unexpected();
    }

    public ClassifiedVendorFailure unexpected() {
        return simpleFailure(CollectionFailureCategory.UNEXPECTED, UNEXPECTED_MESSAGE);
    }

    private ClassifiedVendorFailure classifyVendorFailure(VendorMetricException vendorFailure) {
        CollectionFailureCategory category = mapCategory(vendorFailure.category());
        CollectionFailure failure = new CollectionFailure(
                category, vendorFailure.getMessage(), vendorFailure.retryAfterSeconds());
        List<CollectionWarning> warnings = vendorFailure.warnings().stream()
                .map(warning -> new CollectionWarning(
                        warning.code().name(), warning.message()))
                .toList();
        return new ClassifiedVendorFailure(failure, warnings);
    }

    private ClassifiedVendorFailure simpleFailure(
            CollectionFailureCategory category, String message) {
        return new ClassifiedVendorFailure(
                new CollectionFailure(category, message, null), List.of());
    }

    private CollectionFailureCategory mapCategory(VendorFailureCategory category) {
        return switch (category) {
            case TIMEOUT -> CollectionFailureCategory.TIMEOUT;
            case RATE_LIMITED -> CollectionFailureCategory.RATE_LIMITED;
            case INVALID_DATA -> CollectionFailureCategory.INVALID_DATA;
            case TRANSIENT -> CollectionFailureCategory.TRANSIENT;
            case UNEXPECTED -> CollectionFailureCategory.UNEXPECTED;
        };
    }
}
