package com.example.connectedappliance.appliance.application.exception;

/** Indicates that no appliance exists for a requested public identifier. */
public final class ApplianceNotFoundException extends RuntimeException {

    public ApplianceNotFoundException() {
        super("The appliance was not found.");
    }
}
