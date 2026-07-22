package com.example.connectedappliance.metrics.application.control;

import java.time.Duration;
import java.util.Objects;

/** Pure overflow-safe capped exponential-backoff calculation. */
public final class CollectionBackoffPolicy {

    public static final int MIN_INTERVAL_SECONDS = 5;
    public static final int MAX_INTERVAL_SECONDS = 86_400;

    private final long capSeconds;

    public CollectionBackoffPolicy(Duration backoffCap) {
        Objects.requireNonNull(backoffCap, "backoffCap must not be null");
        if (backoffCap.isNegative() || backoffCap.isZero() || backoffCap.getNano() != 0) {
            throw new IllegalArgumentException(
                    "backoffCap must be a positive whole-second duration");
        }
        this.capSeconds = backoffCap.getSeconds();
    }

    public Duration calculate(int collectionIntervalSeconds, int consecutiveFailures) {
        validateInterval(collectionIntervalSeconds);
        if (consecutiveFailures < 0) {
            throw new IllegalArgumentException("consecutiveFailures must not be negative");
        }

        long delay = Math.min((long) collectionIntervalSeconds, capSeconds);
        int remainingDoublings = consecutiveFailures;
        while (remainingDoublings > 0 && delay < capSeconds) {
            if (delay > capSeconds / 2) {
                return Duration.ofSeconds(capSeconds);
            }
            delay *= 2;
            remainingDoublings--;
        }
        return Duration.ofSeconds(delay);
    }

    static void validateInterval(int collectionIntervalSeconds) {
        if (collectionIntervalSeconds < MIN_INTERVAL_SECONDS
                || collectionIntervalSeconds > MAX_INTERVAL_SECONDS) {
            throw new IllegalArgumentException(
                    "collectionIntervalSeconds must be between 5 and 86400");
        }
    }
}
