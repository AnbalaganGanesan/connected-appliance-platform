package com.example.connectedappliance.vendor.application;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.example.connectedappliance.appliance.application.port.out.SupportedVendorPort;
import com.example.connectedappliance.metrics.application.port.out.VendorMetricBatch;
import com.example.connectedappliance.metrics.application.port.out.VendorMetricRequest;
import com.example.connectedappliance.metrics.application.port.out.VendorMetricSourcePort;
import com.example.connectedappliance.vendor.application.port.VendorAdapter;

/** Immutable exact-key registry for all configured vendor adapters. */
@Component
public final class VendorAdapterRegistry implements SupportedVendorPort, VendorMetricSourcePort {

    private static final String INVALID_REGISTRATION_MESSAGE =
            "Vendor adapter registration is invalid.";
    private static final String DUPLICATE_REGISTRATION_MESSAGE =
            "Duplicate vendor adapter registration.";
    private static final String UNSUPPORTED_VENDOR_MESSAGE = "Vendor adapter is not supported.";

    private final Map<String, VendorAdapter> adaptersByKey;

    public VendorAdapterRegistry(List<VendorAdapter> adapters) {
        Objects.requireNonNull(adapters, INVALID_REGISTRATION_MESSAGE);

        Map<String, VendorAdapter> registeredAdapters = new HashMap<>();
        for (VendorAdapter adapter : adapters) {
            if (adapter == null) {
                throw new IllegalStateException(INVALID_REGISTRATION_MESSAGE);
            }

            String vendorKey = adapter.vendorKey();
            if (vendorKey == null || vendorKey.isBlank()) {
                throw new IllegalStateException(INVALID_REGISTRATION_MESSAGE);
            }

            if (registeredAdapters.putIfAbsent(vendorKey, adapter) != null) {
                throw new IllegalStateException(DUPLICATE_REGISTRATION_MESSAGE);
            }
        }

        adaptersByKey = Map.copyOf(registeredAdapters);
    }

    @Override
    public boolean isSupported(String vendorKey) {
        return vendorKey != null
                && !vendorKey.isBlank()
                && adaptersByKey.containsKey(vendorKey);
    }

    @Override
    public VendorMetricBatch collect(VendorMetricRequest request) {
        Objects.requireNonNull(request, "Vendor metric request must not be null.");

        VendorAdapter adapter = adaptersByKey.get(request.vendorKey());
        if (adapter == null) {
            throw new IllegalArgumentException(UNSUPPORTED_VENDOR_MESSAGE);
        }

        return adapter.collect(request.externalReference());
    }
}
