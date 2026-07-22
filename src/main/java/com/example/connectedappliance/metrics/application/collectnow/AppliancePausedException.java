package com.example.connectedappliance.metrics.application.collectnow;

import com.example.connectedappliance.shared.error.ApiException;
import com.example.connectedappliance.shared.error.CommonApiProblems;

/** Indicates that manual collection cannot begin while the Appliance is paused. */
public final class AppliancePausedException extends ApiException {

    public AppliancePausedException() {
        super(CommonApiProblems.APPLIANCE_PAUSED);
    }
}
