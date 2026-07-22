package com.example.connectedappliance.metrics.application.control;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Combines immediate overlap coordination, bounded submission and typed failure handling. */
public final class GuardedVendorExecution {

    private final ApplianceCollectionGuard guard;
    private final VendorTaskExecutor executor;
    private final VendorFailureClassifier failureClassifier;
    private final long timeoutSeconds;

    public GuardedVendorExecution(
            ApplianceCollectionGuard guard,
            VendorTaskExecutor executor,
            VendorFailureClassifier failureClassifier,
            Duration timeout) {
        this.guard = Objects.requireNonNull(guard, "guard must not be null");
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
        this.failureClassifier =
                Objects.requireNonNull(failureClassifier, "failureClassifier must not be null");
        Objects.requireNonNull(timeout, "timeout must not be null");
        if (timeout.isNegative() || timeout.isZero() || timeout.getNano() != 0) {
            throw new IllegalArgumentException("timeout must be a positive whole-second duration");
        }
        this.timeoutSeconds = timeout.getSeconds();
    }

    public <T> VendorExecutionResult<T> execute(UUID applianceId, Callable<T> vendorCall) {
        Objects.requireNonNull(applianceId, "applianceId must not be null");
        Objects.requireNonNull(vendorCall, "vendorCall must not be null");

        var permit = guard.tryAcquire(applianceId);
        if (permit.isEmpty()) {
            return new VendorExecutionResult.Busy<>();
        }

        try (ApplianceCollectionGuard.Permit ignored = permit.orElseThrow()) {
            Future<T> future;
            try {
                future = executor.submit(vendorCall);
            } catch (RejectedExecutionException rejected) {
                return new VendorExecutionResult.Saturated<>();
            } catch (RuntimeException unexpectedSubmissionFailure) {
                return failed(failureClassifier.unexpected());
            }

            if (future == null) {
                return failed(failureClassifier.unexpected());
            }

            try {
                T value = future.get(timeoutSeconds, TimeUnit.SECONDS);
                if (value == null) {
                    return failed(failureClassifier.unexpected());
                }
                return new VendorExecutionResult.Completed<>(value);
            } catch (TimeoutException timeout) {
                cancelSafely(future);
                return failed(failureClassifier.classify(timeout));
            } catch (InterruptedException interrupted) {
                cancelSafely(future);
                Thread.currentThread().interrupt();
                return failed(failureClassifier.classify(interrupted));
            } catch (ExecutionException executionFailure) {
                Throwable cause = executionFailure.getCause();
                return failed(cause == null
                        ? failureClassifier.unexpected()
                        : failureClassifier.classify(cause));
            } catch (CancellationException cancelled) {
                return failed(failureClassifier.classify(cancelled));
            } catch (RuntimeException unexpectedFutureFailure) {
                return failed(failureClassifier.unexpected());
            }
        }
    }

    private <T> VendorExecutionResult<T> failed(ClassifiedVendorFailure failure) {
        return new VendorExecutionResult.Failed<>(failure);
    }

    private void cancelSafely(Future<?> future) {
        try {
            future.cancel(true);
        } catch (RuntimeException ignored) {
            // The classified timeout/interruption remains the stable outward result.
        }
    }
}
