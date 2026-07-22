package com.example.connectedappliance.vendor.infrastructure.delay;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.example.connectedappliance.metrics.application.port.out.VendorFailureCategory;
import com.example.connectedappliance.metrics.application.port.out.VendorMetricException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThreadVendorDelayTest {

    private final ThreadVendorDelay delay = new ThreadVendorDelay();

    @Test
    void zeroDelayReturnsImmediatelyAndNegativeDelayIsRejected() {
        delay.pause(Duration.ZERO);

        assertThrows(IllegalArgumentException.class, () -> delay.pause(Duration.ofNanos(-1)));
    }

    @Test
    void interruptionRestoresFlagAndBecomesSanitizedTransientFailure() {
        Thread.currentThread().interrupt();
        try {
            VendorMetricException failure = assertThrows(
                    VendorMetricException.class,
                    () -> delay.pause(Duration.ofNanos(1)));

            assertEquals(VendorFailureCategory.TRANSIENT, failure.category());
            assertEquals("The vendor request failed temporarily.", failure.getMessage());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }
}
