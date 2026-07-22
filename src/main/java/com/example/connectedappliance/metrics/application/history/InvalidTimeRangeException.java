package com.example.connectedappliance.metrics.application.history;

import com.example.connectedappliance.shared.error.ApiException;
import com.example.connectedappliance.shared.error.CommonApiProblems;

/** Indicates that parsed history boundaries do not form a non-empty range. */
public final class InvalidTimeRangeException extends ApiException {

    public InvalidTimeRangeException() {
        super(CommonApiProblems.INVALID_TIME_RANGE);
    }
}
