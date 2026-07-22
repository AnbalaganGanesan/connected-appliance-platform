package com.example.connectedappliance.vendor.application.port;

import java.time.Duration;

/** Vendor-internal boundary for applying configured artificial delay. */
public interface VendorDelay {

    void pause(Duration delay);
}
