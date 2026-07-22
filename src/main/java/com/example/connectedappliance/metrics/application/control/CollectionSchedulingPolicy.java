package com.example.connectedappliance.metrics.application.control;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import com.example.connectedappliance.metrics.domain.CollectionFailureCategory;

/** Pure post-outcome failure-count and next-due calculation. */
public final class CollectionSchedulingPolicy {

    private final CollectionBackoffPolicy backoffPolicy;

    public CollectionSchedulingPolicy(CollectionBackoffPolicy backoffPolicy) {
        this.backoffPolicy =
                Objects.requireNonNull(backoffPolicy, "backoffPolicy must not be null");
    }

    public CollectionScheduleDecision afterSuccess(
            Instant completedAt, int collectionIntervalSeconds) {
        return afterNonFailure(completedAt, collectionIntervalSeconds);
    }

    public CollectionScheduleDecision afterPartialSuccess(
            Instant completedAt, int collectionIntervalSeconds) {
        return afterNonFailure(completedAt, collectionIntervalSeconds);
    }

    public CollectionScheduleDecision afterFailure(
            Instant completedAt,
            int collectionIntervalSeconds,
            int currentConsecutiveFailureCount,
            ClassifiedVendorFailure classifiedFailure) {
        Objects.requireNonNull(completedAt, "completedAt must not be null");
        Objects.requireNonNull(classifiedFailure, "classifiedFailure must not be null");
        if (currentConsecutiveFailureCount < 0) {
            throw new IllegalArgumentException(
                    "currentConsecutiveFailureCount must not be negative");
        }

        int updatedFailureCount = currentConsecutiveFailureCount == Integer.MAX_VALUE
                ? Integer.MAX_VALUE
                : currentConsecutiveFailureCount + 1;
        Duration delay = backoffPolicy.calculate(collectionIntervalSeconds, updatedFailureCount);
        if (classifiedFailure.failure().category() == CollectionFailureCategory.RATE_LIMITED
                && classifiedFailure.failure().retryAfterSeconds() != null) {
            delay = max(
                    delay,
                    Duration.ofSeconds(classifiedFailure.failure().retryAfterSeconds()));
        }
        return new CollectionScheduleDecision(
                updatedFailureCount, completedAt.plus(delay));
    }

    private CollectionScheduleDecision afterNonFailure(
            Instant completedAt, int collectionIntervalSeconds) {
        Objects.requireNonNull(completedAt, "completedAt must not be null");
        CollectionBackoffPolicy.validateInterval(collectionIntervalSeconds);
        return new CollectionScheduleDecision(
                0, completedAt.plusSeconds(collectionIntervalSeconds));
    }

    private Duration max(Duration left, Duration right) {
        return left.compareTo(right) >= 0 ? left : right;
    }
}
