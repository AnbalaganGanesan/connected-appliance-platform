package com.example.connectedappliance.vendor.application.port;

import com.example.connectedappliance.metrics.application.port.out.VendorMetricBatch;

/** Internal contract implemented by each Vendor module adapter. */
public interface VendorAdapter {

    String vendorKey();

    VendorMetricBatch collect(String externalReference);
}
