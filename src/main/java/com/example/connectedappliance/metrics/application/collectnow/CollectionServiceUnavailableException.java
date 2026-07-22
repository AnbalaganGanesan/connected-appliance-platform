package com.example.connectedappliance.metrics.application.collectnow;

import com.example.connectedappliance.shared.error.ApiException;
import com.example.connectedappliance.shared.error.CommonApiProblems;

/** Indicates that bounded collection execution rejected work before vendor invocation. */
public final class CollectionServiceUnavailableException extends ApiException {

    public CollectionServiceUnavailableException() {
        super(CommonApiProblems.SERVICE_UNAVAILABLE);
    }
}
