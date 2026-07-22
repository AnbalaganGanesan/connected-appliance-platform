package com.example.connectedappliance.metrics.application.control;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.example.connectedappliance.metrics.domain.CollectionFailure;
import com.example.connectedappliance.metrics.domain.CollectionFailureCategory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class CollectionSchedulingPolicyTest {

    private static final Instant COMPLETED_AT = Instant.parse("2026-07-22T10:00:00Z");
    private static final int INTERVAL_SECONDS = 30;

    private final CollectionSchedulingPolicy policy = new CollectionSchedulingPolicy(
            new CollectionBackoffPolicy(Duration.ofHours(24)));

    @Test
    void successAndPartialSuccessResetFailuresAndUseNormalInterval() {
        CollectionScheduleDecision success =
                policy.afterSuccess(COMPLETED_AT, INTERVAL_SECONDS);
        CollectionScheduleDecision partial =
                policy.afterPartialSuccess(COMPLETED_AT, INTERVAL_SECONDS);

        assertThat(success.consecutiveFailureCount()).isZero();
        assertThat(success.nextCollectionDueAt()).isEqualTo(COMPLETED_AT.plusSeconds(30));
        assertThat(partial).isEqualTo(success);
    }

    @ParameterizedTest
    @EnumSource(
            value = CollectionFailureCategory.class,
            names = {"TIMEOUT", "INVALID_DATA", "TRANSIENT", "UNEXPECTED"})
    void nonRateLimitedFailuresIncrementOnceAndApplyCappedBackoff(
            CollectionFailureCategory category) {
        CollectionScheduleDecision decision = policy.afterFailure(
                COMPLETED_AT, INTERVAL_SECONDS, 0, failure(category, null));

        assertThat(decision.consecutiveFailureCount()).isEqualTo(1);
        assertThat(decision.nextCollectionDueAt()).isEqualTo(COMPLETED_AT.plusSeconds(60));
    }

    @Test
    void rateLimitUsesBackoffForNullShorterAndEqualRetryAfter() {
        assertRateLimitDue(null, 60);
        assertRateLimitDue(30, 60);
        assertRateLimitDue(60, 60);
    }

    @Test
    void rateLimitUsesLongerVendorGuidanceEvenAboveBackoffCap() {
        assertRateLimitDue(120, 120);

        CollectionSchedulingPolicy smallCapPolicy = new CollectionSchedulingPolicy(
                new CollectionBackoffPolicy(Duration.ofSeconds(10)));
        CollectionScheduleDecision decision = smallCapPolicy.afterFailure(
                COMPLETED_AT, INTERVAL_SECONDS, 0,
                failure(CollectionFailureCategory.RATE_LIMITED, 90));

        assertThat(decision.nextCollectionDueAt()).isEqualTo(COMPLETED_AT.plusSeconds(90));
    }

    @Test
    void saturatedFailureCountDoesNotOverflowAndDelayRemainsCapped() {
        CollectionScheduleDecision decision = policy.afterFailure(
                COMPLETED_AT,
                86_400,
                Integer.MAX_VALUE,
                failure(CollectionFailureCategory.TIMEOUT, null));

        assertThat(decision.consecutiveFailureCount()).isEqualTo(Integer.MAX_VALUE);
        assertThat(decision.nextCollectionDueAt())
                .isEqualTo(COMPLETED_AT.plus(Duration.ofHours(24)));
    }

    @Test
    void validatesInputsAndScheduleDecision() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> policy.afterSuccess(COMPLETED_AT, 4));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> policy.afterFailure(
                        COMPLETED_AT,
                        INTERVAL_SECONDS,
                        -1,
                        failure(CollectionFailureCategory.TIMEOUT, null)));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new CollectionScheduleDecision(-1, COMPLETED_AT));
    }

    private void assertRateLimitDue(Integer retryAfter, long expectedDelaySeconds) {
        CollectionScheduleDecision decision = policy.afterFailure(
                COMPLETED_AT,
                INTERVAL_SECONDS,
                0,
                failure(CollectionFailureCategory.RATE_LIMITED, retryAfter));

        assertThat(decision.consecutiveFailureCount()).isEqualTo(1);
        assertThat(decision.nextCollectionDueAt())
                .isEqualTo(COMPLETED_AT.plusSeconds(expectedDelaySeconds));
    }

    private ClassifiedVendorFailure failure(
            CollectionFailureCategory category, Integer retryAfterSeconds) {
        return new ClassifiedVendorFailure(
                new CollectionFailure(category, "sanitized", retryAfterSeconds), List.of());
    }
}
