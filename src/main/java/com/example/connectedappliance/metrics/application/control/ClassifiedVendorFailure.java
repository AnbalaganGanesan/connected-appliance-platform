package com.example.connectedappliance.metrics.application.control;

import java.util.List;
import java.util.Objects;

import com.example.connectedappliance.metrics.domain.CollectionFailure;
import com.example.connectedappliance.metrics.domain.CollectionWarning;

/** Sanitized persistence-neutral failure and its ordered warnings. */
public record ClassifiedVendorFailure(
        CollectionFailure failure, List<CollectionWarning> warnings) {

    public ClassifiedVendorFailure {
        Objects.requireNonNull(failure, "failure must not be null");
        warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings must not be null"));
    }
}
