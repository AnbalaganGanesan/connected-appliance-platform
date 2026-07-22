package com.example.connectedappliance.metrics.infrastructure.execution;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;

import com.example.connectedappliance.metrics.application.control.VendorTaskExecutor;

/** Adapts the dedicated bounded thread pool to the Metrics submission boundary. */
final class ThreadPoolVendorTaskExecutor implements VendorTaskExecutor {

    private final ThreadPoolExecutor executor;

    ThreadPoolVendorTaskExecutor(ThreadPoolExecutor executor) {
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        return executor.submit(Objects.requireNonNull(task, "task must not be null"));
    }
}
