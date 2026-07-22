package com.example.connectedappliance.appliance.application.exception;

/** Indicates that a syntactically valid vendor key has no configured adapter. */
public final class UnsupportedVendorException extends RuntimeException {

    public UnsupportedVendorException() {
        super("The supplied vendor is not supported.");
    }
}
