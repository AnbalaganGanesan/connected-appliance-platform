package com.example.connectedappliance.appliance.application.exception;

/** Indicates that the approved vendor/external-reference identity is already registered. */
public final class DuplicateApplianceException extends RuntimeException {

    public DuplicateApplianceException() {
        super("The appliance is already registered.");
    }
}
