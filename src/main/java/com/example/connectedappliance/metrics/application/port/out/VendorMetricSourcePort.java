package com.example.connectedappliance.metrics.application.port.out;

/** Collects canonical readings for a vendor-neutral appliance target. */
public interface VendorMetricSourcePort {

    VendorMetricBatch collect(VendorMetricRequest request);
}
