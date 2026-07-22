package com.example.connectedappliance.metrics.application.control;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;

/** Minimal submission boundary for deterministic guarded-execution tests. */
public interface VendorTaskExecutor {

    <T> Future<T> submit(Callable<T> task);
}
