package com.example.connectedappliance.metrics.application.collectnow;

import com.example.connectedappliance.shared.error.ApiException;
import com.example.connectedappliance.shared.error.CommonApiProblems;

/** Indicates that the per-Appliance overlap guard is already held. */
public final class CollectionAlreadyInProgressException extends ApiException {

    public CollectionAlreadyInProgressException() {
        super(CommonApiProblems.COLLECTION_ALREADY_IN_PROGRESS);
    }
}
