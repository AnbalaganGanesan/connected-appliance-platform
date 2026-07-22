package com.example.connectedappliance.vendor.infrastructure.delay;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import com.example.connectedappliance.metrics.application.port.out.VendorFailureCategory;
import com.example.connectedappliance.metrics.application.port.out.VendorMetricException;
import com.example.connectedappliance.vendor.application.port.VendorDelay;

/** Interruptible production implementation of configured mock-vendor delay. */
@Component
public final class ThreadVendorDelay implements VendorDelay {

    private static final String INTERRUPTED_MESSAGE = "The vendor request failed temporarily.";

    @Override
    public void pause(Duration delay) {
        Objects.requireNonNull(delay, "delay must not be null");
        if (delay.isNegative()) {
            throw new IllegalArgumentException("delay must not be negative");
        }
        if (delay.isZero()) {
            return;
        }

        try {
            TimeUnit.SECONDS.sleep(delay.getSeconds());
            TimeUnit.NANOSECONDS.sleep(delay.getNano());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new VendorMetricException(
                    VendorFailureCategory.TRANSIENT,
                    INTERRUPTED_MESSAGE,
                    null,
                    List.of());
        }
    }
}
