package com.example.connectedappliance.metrics.application.collection;

/** Sanitized internal failure raised when locked Appliance finalization cannot complete. */
public final class CollectionFinalizationException extends RuntimeException {

    public CollectionFinalizationException() {
        super("Collection finalization could not be completed.");
    }
}
