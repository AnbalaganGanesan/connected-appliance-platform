package com.example.connectedappliance.metrics.api;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.example.connectedappliance.metrics.domain.CollectionAttempt;
import com.example.connectedappliance.metrics.domain.CollectionFailure;
import com.example.connectedappliance.metrics.domain.CollectionFailureCategory;
import com.example.connectedappliance.metrics.domain.CollectionOutcome;
import com.example.connectedappliance.metrics.domain.CollectionTrigger;
import com.example.connectedappliance.metrics.domain.CollectionWarning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CollectionAttemptApiMapperTest {

    private static final UUID ATTEMPT_ID =
            UUID.fromString("8da9a201-85f4-4f8a-b9ec-a49f79f68361");
    private static final UUID APPLIANCE_ID =
            UUID.fromString("2f1b71b7-71a1-4b6c-9d68-54ed3bc24618");
    private static final Instant STARTED = Instant.parse("2026-07-22T10:00:00Z");
    private static final Instant COMPLETED = Instant.parse("2026-07-22T10:00:01Z");
    private static final Instant DUE = Instant.parse("2026-07-22T10:00:31Z");

    private final CollectionAttemptApiMapper mapper = new CollectionAttemptApiMapper();

    @Test
    void mapsSuccessWithExactTopLevelContractAndNoFailure() {
        CollectionAttemptResponse response = mapper.toResponse(attempt(
                CollectionOutcome.SUCCESS, 2, List.of(), null, DUE));

        assertThat(response.id()).isEqualTo(ATTEMPT_ID);
        assertThat(response.applianceId()).isEqualTo(APPLIANCE_ID);
        assertThat(response.trigger()).isEqualTo(CollectionTrigger.MANUAL);
        assertThat(response.outcome()).isEqualTo(CollectionOutcome.SUCCESS);
        assertThat(response.startedAt()).isEqualTo(STARTED);
        assertThat(response.completedAt()).isEqualTo(COMPLETED);
        assertThat(response.sampleCount()).isEqualTo(2);
        assertThat(response.warnings()).isEmpty();
        assertThat(response.failure()).isNull();
        assertThat(response.nextCollectionDueAt()).isEqualTo(DUE);
        assertThat(Arrays.stream(CollectionAttemptResponse.class.getRecordComponents())
                        .map(RecordComponent::getName))
                .containsExactly(
                        "id", "applianceId", "trigger", "outcome", "startedAt",
                        "completedAt", "sampleCount", "warnings", "failure",
                        "nextCollectionDueAt");
    }

    @Test
    void mapsPartialSuccessWarningsInPersistedOrder() {
        CollectionAttemptResponse response = mapper.toResponse(attempt(
                CollectionOutcome.PARTIAL_SUCCESS,
                1,
                List.of(
                        new CollectionWarning("UNKNOWN_METRIC", "Unknown metric."),
                        new CollectionWarning("MALFORMED_VALUE", "Malformed value.")),
                null,
                DUE));

        assertThat(response.warnings())
                .extracting(CollectionWarningResponse::code, CollectionWarningResponse::message)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("UNKNOWN_METRIC", "Unknown metric."),
                        org.assertj.core.groups.Tuple.tuple("MALFORMED_VALUE", "Malformed value."));
        assertThat(response.failure()).isNull();
    }

    @Test
    void mapsTimeoutFailureAndPreservesNullRetryAndDue() {
        CollectionAttemptResponse response = mapper.toResponse(attempt(
                CollectionOutcome.FAILED,
                0,
                List.of(new CollectionWarning("MALFORMED_VALUE", "Malformed value.")),
                new CollectionFailure(
                        CollectionFailureCategory.TIMEOUT, "The vendor request timed out.", null),
                null));

        assertThat(response.failure().category()).isEqualTo(CollectionFailureCategory.TIMEOUT);
        assertThat(response.failure().message()).isEqualTo("The vendor request timed out.");
        assertThat(response.failure().retryAfterSeconds()).isNull();
        assertThat(response.nextCollectionDueAt()).isNull();
    }

    @Test
    void mapsRateLimitRetryAfterExactly() {
        CollectionAttemptResponse response = mapper.toResponse(attempt(
                CollectionOutcome.FAILED,
                0,
                List.of(),
                new CollectionFailure(
                        CollectionFailureCategory.RATE_LIMITED,
                        "The vendor rate limit was reached.",
                        90),
                DUE));

        assertThat(response.failure().retryAfterSeconds()).isEqualTo(90);
    }

    @Test
    void mapsUnexpectedSanitizedMessageWithoutThrowableSurface() {
        CollectionAttemptResponse response = mapper.toResponse(attempt(
                CollectionOutcome.FAILED,
                0,
                List.of(),
                new CollectionFailure(
                        CollectionFailureCategory.UNEXPECTED,
                        "The vendor request failed unexpectedly.",
                        null),
                DUE));

        assertThat(response.failure().message())
                .isEqualTo("The vendor request failed unexpectedly.");
        assertThat(Arrays.stream(CollectionFailureResponse.class.getRecordComponents())
                        .map(RecordComponent::getName))
                .containsExactly("category", "message", "retryAfterSeconds");
        assertThat(Arrays.stream(CollectionWarningResponse.class.getRecordComponents())
                        .map(RecordComponent::getName))
                .containsExactly("code", "message");
    }

    @Test
    void responseWarningListIsAnImmutableCopy() {
        CollectionAttemptResponse response = mapper.toResponse(attempt(
                CollectionOutcome.PARTIAL_SUCCESS,
                1,
                List.of(new CollectionWarning("UNKNOWN_METRIC", "Unknown metric.")),
                null,
                DUE));

        assertThatThrownBy(() -> response.warnings().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private CollectionAttempt attempt(
            CollectionOutcome outcome,
            int sampleCount,
            List<CollectionWarning> warnings,
            CollectionFailure failure,
            Instant due) {
        return new CollectionAttempt(
                ATTEMPT_ID,
                APPLIANCE_ID,
                CollectionTrigger.MANUAL,
                outcome,
                STARTED,
                COMPLETED,
                sampleCount,
                warnings,
                failure,
                due);
    }
}
