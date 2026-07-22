package com.example.connectedappliance.vendor.application.port;

import java.util.List;

import com.example.connectedappliance.shared.metric.CanonicalMetricReading;

/** Internal contract implemented by each Vendor module adapter. */
public interface VendorAdapter {

    String vendorKey();

    List<CanonicalMetricReading> collect(String externalReference);
}
