package com.example.connectedappliance.metrics.application.control;

import java.util.Objects;

/** Persistence-neutral result of one guarded vendor-call execution. */
public sealed interface VendorExecutionResult<T>
        permits VendorExecutionResult.Completed,
                VendorExecutionResult.Failed,
                VendorExecutionResult.Busy,
                VendorExecutionResult.Saturated {

    record Completed<T>(T value) implements VendorExecutionResult<T> {

        public Completed {
            Objects.requireNonNull(value, "value must not be null");
        }
    }

    record Failed<T>(ClassifiedVendorFailure failure) implements VendorExecutionResult<T> {

        public Failed {
            Objects.requireNonNull(failure, "failure must not be null");
        }
    }

    record Busy<T>() implements VendorExecutionResult<T> {}

    record Saturated<T>() implements VendorExecutionResult<T> {}
}
