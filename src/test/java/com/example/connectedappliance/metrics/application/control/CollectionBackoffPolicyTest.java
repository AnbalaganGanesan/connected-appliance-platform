package com.example.connectedappliance.metrics.application.control;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class CollectionBackoffPolicyTest {

    private static final Duration DEFAULT_CAP = Duration.ofHours(24);
    private final CollectionBackoffPolicy policy = new CollectionBackoffPolicy(DEFAULT_CAP);

    @Test
    void calculatesApprovedThirtySecondSequence() {
        assertThat(policy.calculate(30, 0)).isEqualTo(Duration.ofSeconds(30));
        assertThat(policy.calculate(30, 1)).isEqualTo(Duration.ofSeconds(60));
        assertThat(policy.calculate(30, 2)).isEqualTo(Duration.ofSeconds(120));
        assertThat(policy.calculate(30, 3)).isEqualTo(Duration.ofSeconds(240));
    }

    @Test
    void handlesExactCapBelowCapAndWouldExceedCap() {
        CollectionBackoffPolicy smallCap =
                new CollectionBackoffPolicy(Duration.ofSeconds(120));

        assertThat(smallCap.calculate(30, 2)).isEqualTo(Duration.ofSeconds(120));
        assertThat(smallCap.calculate(59, 1)).isEqualTo(Duration.ofSeconds(118));
        assertThat(smallCap.calculate(61, 1)).isEqualTo(Duration.ofSeconds(120));
    }

    @Test
    void capsIntervalWhenConfiguredCapIsSmaller() {
        CollectionBackoffPolicy smallCap =
                new CollectionBackoffPolicy(Duration.ofSeconds(10));

        assertThat(smallCap.calculate(30, 0)).isEqualTo(Duration.ofSeconds(10));
        assertThat(smallCap.calculate(30, 100)).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void safelyCapsMaximumIntervalAndVeryLargeFailureCounts() {
        assertThat(policy.calculate(86_400, 0)).isEqualTo(DEFAULT_CAP);
        assertThat(policy.calculate(30, 1_000_000)).isEqualTo(DEFAULT_CAP);
        assertThat(policy.calculate(30, Integer.MAX_VALUE)).isEqualTo(DEFAULT_CAP);
    }

    @Test
    void rejectsInvalidIntervalsFailureCountsAndCaps() {
        assertThatIllegalArgumentException().isThrownBy(() -> policy.calculate(0, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> policy.calculate(-1, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> policy.calculate(4, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> policy.calculate(86_401, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> policy.calculate(30, -1));
        assertThatNullPointerException().isThrownBy(() -> new CollectionBackoffPolicy(null));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new CollectionBackoffPolicy(Duration.ZERO));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new CollectionBackoffPolicy(Duration.ofSeconds(-1)));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new CollectionBackoffPolicy(Duration.ofMillis(1500)));
    }
}
