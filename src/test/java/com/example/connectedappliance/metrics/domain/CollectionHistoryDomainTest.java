package com.example.connectedappliance.metrics.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CollectionHistoryDomainTest {

    private static final Instant STARTED = Instant.parse("2026-07-21T10:00:00Z");
    private static final Instant COMPLETED = STARTED.plusSeconds(2);
    private static final CollectionWarning WARNING =
            new CollectionWarning("UNKNOWN_METRIC", "Unsupported reading omitted");

    @Test
    void exposesOnlyTheApprovedCollectionEnums() {
        assertThat(CollectionTrigger.values()).containsExactly(MANUAL, SCHEDULED);
        assertThat(CollectionOutcome.values()).containsExactly(SUCCESS, PARTIAL_SUCCESS, FAILED);
        assertThat(CollectionFailureCategory.values())
                .containsExactly(TIMEOUT, RATE_LIMITED, INVALID_DATA, TRANSIENT, UNEXPECTED);
    }

    @Test
    void validatesWarningsWithoutNormalizingStoredValues() {
        CollectionWarning warning = new CollectionWarning("CUSTOM_CODE", "  Safe message  ");
        assertThat(warning.code()).isEqualTo("CUSTOM_CODE");
        assertThat(warning.message()).isEqualTo("  Safe message  ");

        for (String invalid : List.of("", "lower", "_PREFIX", "HAS-HYPHEN", "HAS SPACE")) {
            assertThatThrownBy(() -> new CollectionWarning(invalid, "message"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> new CollectionWarning("A".repeat(65), "message"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CollectionWarning(null, "message"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CollectionWarning("CODE", null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CollectionWarning("CODE", " \t "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CollectionWarning("CODE", "m".repeat(501)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validatesFailureCategoriesMessagesAndRetryAfterRules() {
        for (CollectionFailureCategory category : CollectionFailureCategory.values()) {
            CollectionFailure failure = new CollectionFailure(category, null, null);
            assertThat(failure.category()).isEqualTo(category);
            assertThat(failure.message()).isNull();
        }
        assertThat(new CollectionFailure(RATE_LIMITED, "retry later", 30).retryAfterSeconds())
                .isEqualTo(30);
        assertThat(new CollectionFailure(RATE_LIMITED, null, null).retryAfterSeconds()).isNull();
        assertThatThrownBy(() -> new CollectionFailure(RATE_LIMITED, null, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CollectionFailure(RATE_LIMITED, null, -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CollectionFailure(TIMEOUT, null, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CollectionFailure(TIMEOUT, "m".repeat(501), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CollectionFailure(null, null, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void createsValidSuccessPartialAndFailedAttemptSnapshots() {
        CollectionAttempt success = attempt(SUCCESS, 1, List.of(), null, COMPLETED.plusSeconds(30));
        CollectionAttempt partial = attempt(PARTIAL_SUCCESS, 1, List.of(WARNING), null, null);
        CollectionFailure failure = new CollectionFailure(RATE_LIMITED, "retry later", 10);
        CollectionAttempt failed = attempt(FAILED, 0, List.of(WARNING), failure, null);

        assertThat(success.nextCollectionDueAt()).isEqualTo(COMPLETED.plusSeconds(30));
        assertThat(partial.warnings()).containsExactly(WARNING);
        assertThat(failed.failure()).isEqualTo(failure);
    }

    @Test
    void defensivelyCopiesAndFreezesAttemptWarnings() {
        List<CollectionWarning> source = new ArrayList<>(List.of(WARNING));
        CollectionAttempt attempt = attempt(PARTIAL_SUCCESS, 1, source, null, null);
        source.clear();

        assertThat(attempt.warnings()).containsExactly(WARNING);
        assertThatThrownBy(() -> attempt.warnings().add(WARNING))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> attempt(PARTIAL_SUCCESS, 1, List.of(WARNING, null), null, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsInvalidAttemptTimingAndOutcomeCombinations() {
        assertThatThrownBy(() -> new CollectionAttempt(
                        UUID.randomUUID(), UUID.randomUUID(), MANUAL, SUCCESS,
                        COMPLETED, STARTED, 1, List.of(), null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> attempt(SUCCESS, 1, List.of(), null, COMPLETED.minusNanos(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> attempt(SUCCESS, 0, List.of(), null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> attempt(SUCCESS, 1, List.of(WARNING), null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> attempt(PARTIAL_SUCCESS, 1, List.of(), null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> attempt(
                        PARTIAL_SUCCESS, 1, List.of(WARNING), new CollectionFailure(TIMEOUT, null, null), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> attempt(
                        FAILED, 1, List.of(), new CollectionFailure(TIMEOUT, null, null), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> attempt(FAILED, 0, List.of(), null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private CollectionAttempt attempt(
            CollectionOutcome outcome,
            int sampleCount,
            List<CollectionWarning> warnings,
            CollectionFailure failure,
            Instant dueAt) {
        return new CollectionAttempt(
                UUID.randomUUID(),
                UUID.randomUUID(),
                MANUAL,
                outcome,
                STARTED,
                COMPLETED,
                sampleCount,
                warnings,
                failure,
                dueAt);
    }

    private static final CollectionTrigger MANUAL = CollectionTrigger.MANUAL;
    private static final CollectionTrigger SCHEDULED = CollectionTrigger.SCHEDULED;
    private static final CollectionOutcome SUCCESS = CollectionOutcome.SUCCESS;
    private static final CollectionOutcome PARTIAL_SUCCESS = CollectionOutcome.PARTIAL_SUCCESS;
    private static final CollectionOutcome FAILED = CollectionOutcome.FAILED;
    private static final CollectionFailureCategory TIMEOUT = CollectionFailureCategory.TIMEOUT;
    private static final CollectionFailureCategory RATE_LIMITED = CollectionFailureCategory.RATE_LIMITED;
    private static final CollectionFailureCategory INVALID_DATA = CollectionFailureCategory.INVALID_DATA;
    private static final CollectionFailureCategory TRANSIENT = CollectionFailureCategory.TRANSIENT;
    private static final CollectionFailureCategory UNEXPECTED = CollectionFailureCategory.UNEXPECTED;
}
