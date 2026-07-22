package com.example.connectedappliance.appliance.application.port.out;

/** Answers whether a stable vendor key is supported by the configured vendor integration. */
public interface SupportedVendorPort {

    boolean isSupported(String vendorKey);
}
