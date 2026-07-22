package com.example.connectedappliance.metrics.application.port.out;

import java.util.Objects;

/** Vendor-neutral target information required to collect readings for one appliance. */
public record VendorMetricRequest(String vendorKey, String externalReference) {

    public VendorMetricRequest {
        Objects.requireNonNull(vendorKey, "vendorKey must not be null");
        Objects.requireNonNull(externalReference, "externalReference must not be null");
        if (vendorKey.isBlank()) {
            throw new IllegalArgumentException("vendorKey must not be blank");
        }
        if (externalReference.isBlank()) {
            throw new IllegalArgumentException("externalReference must not be blank");
        }
    }
}
