package com.example.connectedappliance.metrics.application.control;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class InMemoryApplianceCollectionGuardTest {

    private final InMemoryApplianceCollectionGuard guard =
            new InMemoryApplianceCollectionGuard();

    @Test
    void rejectsNullApplianceId() {
        assertThatNullPointerException().isThrownBy(() -> guard.tryAcquire(null));
    }

    @Test
    void firstAcquisitionSucceedsAndSecondSameApplianceFailsImmediately() {
        UUID applianceId = UUID.randomUUID();

        try (ApplianceCollectionGuard.Permit permit =
                guard.tryAcquire(applianceId).orElseThrow()) {
            assertThat(guard.tryAcquire(applianceId)).isEmpty();
        }
    }

    @Test
    void differentAppliancesAcquireIndependently() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        try (ApplianceCollectionGuard.Permit firstPermit =
                        guard.tryAcquire(first).orElseThrow();
                ApplianceCollectionGuard.Permit secondPermit =
                        guard.tryAcquire(second).orElseThrow()) {
            assertThat(guard.tryAcquire(first)).isEmpty();
            assertThat(guard.tryAcquire(second)).isEmpty();
        }
    }

    @Test
    void releaseAllowsImmediateReacquisitionAndCloseIsIdempotent() {
        UUID applianceId = UUID.randomUUID();
        ApplianceCollectionGuard.Permit first = guard.tryAcquire(applianceId).orElseThrow();

        first.close();
        first.close();

        try (ApplianceCollectionGuard.Permit reacquired =
                guard.tryAcquire(applianceId).orElseThrow()) {
            assertThat(guard.tryAcquire(applianceId)).isEmpty();
        }
    }

    @Test
    void staleDoubleCloseCannotReleaseNewerPermit() {
        UUID applianceId = UUID.randomUUID();
        ApplianceCollectionGuard.Permit stale = guard.tryAcquire(applianceId).orElseThrow();
        stale.close();
        ApplianceCollectionGuard.Permit current = guard.tryAcquire(applianceId).orElseThrow();

        try {
            stale.close();
            assertThat(guard.tryAcquire(applianceId)).isEmpty();
        } finally {
            current.close();
        }

        try (ApplianceCollectionGuard.Permit reacquired =
                guard.tryAcquire(applianceId).orElseThrow()) {
            assertThat(guard.tryAcquire(applianceId)).isEmpty();
        }
    }

    @Test
    void concurrentSameApplianceAcquisitionHasExactlyOneWinner() throws Exception {
        UUID applianceId = UUID.randomUUID();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch acquisitionsFinished = new CountDownLatch(2);
        CountDownLatch releaseWinner = new CountDownLatch(1);
        AtomicInteger winners = new AtomicInteger();

        try {
            List<Future<?>> futures = List.of(
                    executor.submit(() -> acquireAndHold(
                            applianceId, start, acquisitionsFinished, releaseWinner, winners)),
                    executor.submit(() -> acquireAndHold(
                            applianceId, start, acquisitionsFinished, releaseWinner, winners)));

            start.countDown();
            assertThat(acquisitionsFinished.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(winners).hasValue(1);
            releaseWinner.countDown();
            for (Future<?> future : futures) {
                future.get(1, TimeUnit.SECONDS);
            }
        } finally {
            releaseWinner.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(1, TimeUnit.SECONDS)).isTrue();
        }

        try (ApplianceCollectionGuard.Permit reacquired =
                guard.tryAcquire(applianceId).orElseThrow()) {
            assertThat(guard.tryAcquire(applianceId)).isEmpty();
        }
    }

    private void acquireAndHold(
            UUID applianceId,
            CountDownLatch start,
            CountDownLatch acquisitionsFinished,
            CountDownLatch releaseWinner,
            AtomicInteger winners) {
        try {
            if (!start.await(Duration.ofSeconds(1).toMillis(), TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("start latch timed out");
            }
            var permit = guard.tryAcquire(applianceId);
            if (permit.isPresent()) {
                winners.incrementAndGet();
            }
            acquisitionsFinished.countDown();
            if (permit.isPresent()) {
                if (!releaseWinner.await(1, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("release latch timed out");
                }
                permit.orElseThrow().close();
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("test thread interrupted");
        }
    }
}
