package com.example.connectedappliance.metrics.application.control;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.example.connectedappliance.metrics.application.port.out.VendorFailureCategory;
import com.example.connectedappliance.metrics.application.port.out.VendorMetricException;
import com.example.connectedappliance.metrics.application.port.out.VendorMetricWarning;
import com.example.connectedappliance.metrics.application.port.out.VendorMetricWarningCode;
import com.example.connectedappliance.metrics.domain.CollectionFailureCategory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class GuardedVendorExecutionTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(2);

    private final InMemoryApplianceCollectionGuard guard =
            new InMemoryApplianceCollectionGuard();
    private final VendorFailureClassifier classifier = new VendorFailureClassifier();

    @Test
    void rejectsNullApplianceAndCallable() {
        GuardedVendorExecution execution = execution(new ImmediateExecutor());

        assertThatNullPointerException().isThrownBy(() -> execution.execute(null, () -> "ok"));
        assertThatNullPointerException()
                .isThrownBy(() -> execution.execute(UUID.randomUUID(), null));
    }

    @Test
    void busyReturnsImmediatelyWithoutSubmissionOrCallableInvocation() {
        UUID applianceId = UUID.randomUUID();
        AtomicInteger callableInvocations = new AtomicInteger();
        ImmediateExecutor executor = new ImmediateExecutor();

        try (ApplianceCollectionGuard.Permit ignored =
                guard.tryAcquire(applianceId).orElseThrow()) {
            VendorExecutionResult<String> result = execution(executor).execute(
                    applianceId,
                    () -> {
                        callableInvocations.incrementAndGet();
                        return "not-called";
                    });

            assertThat(result).isInstanceOf(VendorExecutionResult.Busy.class);
            assertThat(executor.submissions()).isZero();
            assertThat(callableInvocations).hasValue(0);
        }
    }

    @Test
    void completedReturnsValueAndReleasesGuard() {
        UUID applianceId = UUID.randomUUID();
        GuardedVendorExecution execution = execution(new ImmediateExecutor());

        VendorExecutionResult<String> result =
                execution.execute(applianceId, () -> "complete");
        VendorExecutionResult<String> subsequent =
                execution.execute(applianceId, () -> "subsequent");

        assertThat(result).isEqualTo(new VendorExecutionResult.Completed<>("complete"));
        assertThat(subsequent)
                .isEqualTo(new VendorExecutionResult.Completed<>("subsequent"));
        assertReacquirable(applianceId);
    }

    @Test
    void typedVendorFailurePreservesSanitizedFieldsAndReleasesGuard() {
        UUID applianceId = UUID.randomUUID();
        VendorMetricWarning warning = VendorMetricWarning.forCode(
                VendorMetricWarningCode.UNKNOWN_METRIC);

        VendorExecutionResult<String> result = execution(new ImmediateExecutor()).execute(
                applianceId,
                () -> {
                    throw new VendorMetricException(
                            VendorFailureCategory.RATE_LIMITED,
                            "The vendor rate limit was reached.",
                            30,
                            List.of(warning));
                });

        ClassifiedVendorFailure failure = failed(result);
        assertThat(failure.failure().category())
                .isEqualTo(CollectionFailureCategory.RATE_LIMITED);
        assertThat(failure.failure().message())
                .isEqualTo("The vendor rate limit was reached.");
        assertThat(failure.failure().retryAfterSeconds()).isEqualTo(30);
        assertThat(failure.warnings())
                .extracting(warningValue -> warningValue.code())
                .containsExactly("UNKNOWN_METRIC");
        assertReacquirable(applianceId);
    }

    @Test
    void unexpectedFailureIsSanitizedAndReleasesGuard() {
        UUID applianceId = UUID.randomUUID();
        String sensitive = "password=secret SQL internal_appliance_table";

        VendorExecutionResult<String> result = execution(new ImmediateExecutor()).execute(
                applianceId, () -> { throw new IllegalStateException(sensitive); });

        ClassifiedVendorFailure failure = failed(result);
        assertThat(failure.failure().category())
                .isEqualTo(CollectionFailureCategory.UNEXPECTED);
        assertThat(failure.failure().message())
                .isEqualTo(VendorFailureClassifier.UNEXPECTED_MESSAGE)
                .doesNotContain(sensitive, "IllegalStateException");
        assertReacquirable(applianceId);
    }

    @Test
    void saturationDoesNotInvokeCallableOrCreateFailureAndReleasesGuard() {
        UUID applianceId = UUID.randomUUID();
        AtomicInteger callableInvocations = new AtomicInteger();
        AtomicInteger submissions = new AtomicInteger();
        VendorTaskExecutor rejecting = new VendorTaskExecutor() {
            @Override
            public <T> Future<T> submit(Callable<T> task) {
                submissions.incrementAndGet();
                throw new RejectedExecutionException("queue details must not escape");
            }
        };

        VendorExecutionResult<String> result = execution(rejecting).execute(
                applianceId,
                () -> {
                    callableInvocations.incrementAndGet();
                    return "not-called";
                });

        assertThat(result).isInstanceOf(VendorExecutionResult.Saturated.class);
        assertThat(result).isNotInstanceOf(VendorExecutionResult.Failed.class);
        assertThat(submissions).hasValue(1);
        assertThat(callableInvocations).hasValue(0);
        assertThat(execution(new ImmediateExecutor()).execute(applianceId, () -> "subsequent"))
                .isEqualTo(new VendorExecutionResult.Completed<>("subsequent"));
        assertReacquirable(applianceId);
    }

    @Test
    void timeoutCancelsFutureWithoutWaitingAndReleasesGuard() {
        UUID applianceId = UUID.randomUUID();
        ControlledFuture<String> future = ControlledFuture.timeout();

        VendorExecutionResult<String> result =
                execution(new FixedFutureExecutor(future)).execute(applianceId, () -> "unused");

        assertThat(future.cancelledWithInterruption()).isTrue();
        assertThat(failed(result).failure().category())
                .isEqualTo(CollectionFailureCategory.TIMEOUT);
        assertThat(failed(result).failure().message())
                .isEqualTo(VendorFailureClassifier.TIMEOUT_MESSAGE);
        assertReacquirable(applianceId);
    }

    @Test
    void interruptionCancelsFutureRestoresFlagAndReleasesGuard() {
        UUID applianceId = UUID.randomUUID();
        ControlledFuture<String> future = ControlledFuture.interrupted();

        try {
            VendorExecutionResult<String> result = execution(new FixedFutureExecutor(future))
                    .execute(applianceId, () -> "unused");

            assertThat(future.cancelledWithInterruption()).isTrue();
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            assertThat(failed(result).failure().category())
                    .isEqualTo(CollectionFailureCategory.TRANSIENT);
            assertThat(failed(result).failure().message())
                    .isEqualTo(VendorFailureClassifier.TEMPORARY_MESSAGE);
            assertReacquirable(applianceId);
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void cancelledFutureAndNullResultBecomeUnexpectedFailures() {
        UUID cancelledId = UUID.randomUUID();
        UUID nullId = UUID.randomUUID();

        VendorExecutionResult<String> cancelled =
                execution(new FixedFutureExecutor(ControlledFuture.cancelled()))
                        .execute(cancelledId, () -> "unused");
        VendorExecutionResult<String> nullResult =
                execution(new ImmediateExecutor()).execute(nullId, () -> null);

        assertThat(failed(cancelled).failure().category())
                .isEqualTo(CollectionFailureCategory.UNEXPECTED);
        assertThat(failed(nullResult).failure().category())
                .isEqualTo(CollectionFailureCategory.UNEXPECTED);
        assertReacquirable(cancelledId);
        assertReacquirable(nullId);
    }

    @Test
    void resultValuesRejectNullPayloads() {
        assertThatNullPointerException()
                .isThrownBy(() -> new VendorExecutionResult.Completed<>(null));
        assertThatNullPointerException()
                .isThrownBy(() -> new VendorExecutionResult.Failed<>(null));
    }

    private GuardedVendorExecution execution(VendorTaskExecutor executor) {
        return new GuardedVendorExecution(guard, executor, classifier, TIMEOUT);
    }

    private ClassifiedVendorFailure failed(VendorExecutionResult<?> result) {
        assertThat(result).isInstanceOf(VendorExecutionResult.Failed.class);
        return ((VendorExecutionResult.Failed<?>) result).failure();
    }

    private void assertReacquirable(UUID applianceId) {
        try (ApplianceCollectionGuard.Permit ignored =
                guard.tryAcquire(applianceId).orElseThrow()) {
            assertThat(guard.tryAcquire(applianceId)).isEmpty();
        }
    }

    private static final class ImmediateExecutor implements VendorTaskExecutor {

        private final AtomicInteger submissions = new AtomicInteger();

        @Override
        public <T> Future<T> submit(Callable<T> task) {
            submissions.incrementAndGet();
            FutureTask<T> future = new FutureTask<>(task);
            future.run();
            return future;
        }

        int submissions() {
            return submissions.get();
        }
    }

    private record FixedFutureExecutor(Future<?> future) implements VendorTaskExecutor {

        @Override
        @SuppressWarnings("unchecked")
        public <T> Future<T> submit(Callable<T> task) {
            return (Future<T>) future;
        }
    }

    private static final class ControlledFuture<T> implements Future<T> {

        private enum Outcome {
            TIMEOUT,
            INTERRUPTED,
            CANCELLED
        }

        private final Outcome outcome;
        private final AtomicBoolean cancelledWithInterruption = new AtomicBoolean();

        private ControlledFuture(Outcome outcome) {
            this.outcome = outcome;
        }

        static <T> ControlledFuture<T> timeout() {
            return new ControlledFuture<>(Outcome.TIMEOUT);
        }

        static <T> ControlledFuture<T> interrupted() {
            return new ControlledFuture<>(Outcome.INTERRUPTED);
        }

        static <T> ControlledFuture<T> cancelled() {
            return new ControlledFuture<>(Outcome.CANCELLED);
        }

        boolean cancelledWithInterruption() {
            return cancelledWithInterruption.get();
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            cancelledWithInterruption.set(mayInterruptIfRunning);
            return true;
        }

        @Override
        public boolean isCancelled() {
            return outcome == Outcome.CANCELLED || cancelledWithInterruption.get();
        }

        @Override
        public boolean isDone() {
            return true;
        }

        @Override
        public T get() {
            throw new UnsupportedOperationException("untimed get is not used");
        }

        @Override
        public T get(long timeout, TimeUnit unit)
                throws InterruptedException, ExecutionException, TimeoutException {
            switch (outcome) {
                case TIMEOUT -> throw new TimeoutException("deterministic timeout");
                case INTERRUPTED -> throw new InterruptedException("deterministic interruption");
                case CANCELLED -> throw new CancellationException("deterministic cancellation");
            }
            throw new IllegalStateException("unsupported controlled outcome");
        }
    }
}
