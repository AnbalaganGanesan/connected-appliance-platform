package com.example.connectedappliance.metrics.application.collectnow;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.connectedappliance.appliance.application.exception.ApplianceNotFoundException;
import com.example.connectedappliance.metrics.application.collection.CollectApplianceCommand;
import com.example.connectedappliance.metrics.application.collection.CollectionWorkflowResult;
import com.example.connectedappliance.metrics.application.collection.MetricCollectionService;
import com.example.connectedappliance.metrics.domain.CollectionAttempt;
import com.example.connectedappliance.metrics.domain.CollectionFailure;
import com.example.connectedappliance.metrics.domain.CollectionFailureCategory;
import com.example.connectedappliance.metrics.domain.CollectionOutcome;
import com.example.connectedappliance.metrics.domain.CollectionTrigger;
import com.example.connectedappliance.metrics.domain.CollectionWarning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CollectNowServiceTest {

    private static final UUID APPLIANCE_ID =
            UUID.fromString("2f1b71b7-71a1-4b6c-9d68-54ed3bc24618");

    @Mock
    private MetricCollectionService collectionService;

    @InjectMocks
    private CollectNowService service;

    @Test
    void returnsPersistedSuccessAndSuppliesExactManualCommandOnce() {
        CollectionAttempt attempt = successAttempt();
        when(collectionService.collect(new CollectApplianceCommand(
                        APPLIANCE_ID, CollectionTrigger.MANUAL)))
                .thenReturn(new CollectionWorkflowResult.Persisted(attempt));

        assertThat(service.collectNow(APPLIANCE_ID)).isSameAs(attempt);

        verify(collectionService).collect(
                new CollectApplianceCommand(APPLIANCE_ID, CollectionTrigger.MANUAL));
    }

    @Test
    void returnsPersistedPartialAttemptWithWarningsUnchanged() {
        CollectionAttempt attempt = new CollectionAttempt(
                UUID.randomUUID(),
                APPLIANCE_ID,
                CollectionTrigger.MANUAL,
                CollectionOutcome.PARTIAL_SUCCESS,
                Instant.parse("2026-07-22T10:00:00Z"),
                Instant.parse("2026-07-22T10:00:01Z"),
                1,
                List.of(new CollectionWarning("UNKNOWN_METRIC", "A reading was ignored.")),
                null,
                Instant.parse("2026-07-22T10:00:31Z"));
        when(collectionService.collect(new CollectApplianceCommand(
                        APPLIANCE_ID, CollectionTrigger.MANUAL)))
                .thenReturn(new CollectionWorkflowResult.Persisted(attempt));

        assertThat(service.collectNow(APPLIANCE_ID)).isSameAs(attempt);
        assertThat(attempt.warnings()).extracting(CollectionWarning::code)
                .containsExactly("UNKNOWN_METRIC");
    }

    @ParameterizedTest
    @MethodSource("persistedFailures")
    void returnsPersistedVendorFailuresWithoutTurningThemIntoHttpErrors(
            CollectionFailureCategory category, Integer retryAfterSeconds) {
        CollectionAttempt attempt = failedAttempt(category, retryAfterSeconds);
        when(collectionService.collect(new CollectApplianceCommand(
                        APPLIANCE_ID, CollectionTrigger.MANUAL)))
                .thenReturn(new CollectionWorkflowResult.Persisted(attempt));

        assertThat(service.collectNow(APPLIANCE_ID)).isSameAs(attempt);
    }

    @Test
    void mapsNotFoundToExistingApplianceException() {
        when(collectionService.collect(new CollectApplianceCommand(
                        APPLIANCE_ID, CollectionTrigger.MANUAL)))
                .thenReturn(new CollectionWorkflowResult.NotFound());

        assertThatThrownBy(() -> service.collectNow(APPLIANCE_ID))
                .isInstanceOf(ApplianceNotFoundException.class);
    }

    @Test
    void mapsPausedToFocusedConflictException() {
        when(collectionService.collect(new CollectApplianceCommand(
                        APPLIANCE_ID, CollectionTrigger.MANUAL)))
                .thenReturn(new CollectionWorkflowResult.Paused());

        assertThatThrownBy(() -> service.collectNow(APPLIANCE_ID))
                .isInstanceOf(AppliancePausedException.class);
    }

    @Test
    void mapsBusyToFocusedConflictException() {
        when(collectionService.collect(new CollectApplianceCommand(
                        APPLIANCE_ID, CollectionTrigger.MANUAL)))
                .thenReturn(new CollectionWorkflowResult.Busy());

        assertThatThrownBy(() -> service.collectNow(APPLIANCE_ID))
                .isInstanceOf(CollectionAlreadyInProgressException.class);
    }

    @Test
    void mapsSaturationToFocusedServiceUnavailableException() {
        when(collectionService.collect(new CollectApplianceCommand(
                        APPLIANCE_ID, CollectionTrigger.MANUAL)))
                .thenReturn(new CollectionWorkflowResult.Saturated());

        assertThatThrownBy(() -> service.collectNow(APPLIANCE_ID))
                .isInstanceOf(CollectionServiceUnavailableException.class);
    }

    @Test
    void rejectsNullApplianceIdBeforeInvokingWorkflow() {
        assertThatNullPointerException().isThrownBy(() -> service.collectNow(null));
        verifyNoInteractions(collectionService);
    }

    private static Stream<Arguments> persistedFailures() {
        return Stream.of(
                Arguments.of(CollectionFailureCategory.TIMEOUT, null),
                Arguments.of(CollectionFailureCategory.RATE_LIMITED, 90),
                Arguments.of(CollectionFailureCategory.UNEXPECTED, null));
    }

    private CollectionAttempt successAttempt() {
        return new CollectionAttempt(
                UUID.randomUUID(),
                APPLIANCE_ID,
                CollectionTrigger.MANUAL,
                CollectionOutcome.SUCCESS,
                Instant.parse("2026-07-22T10:00:00Z"),
                Instant.parse("2026-07-22T10:00:01Z"),
                2,
                List.of(),
                null,
                Instant.parse("2026-07-22T10:00:31Z"));
    }

    private CollectionAttempt failedAttempt(
            CollectionFailureCategory category, Integer retryAfterSeconds) {
        return new CollectionAttempt(
                UUID.randomUUID(),
                APPLIANCE_ID,
                CollectionTrigger.MANUAL,
                CollectionOutcome.FAILED,
                Instant.parse("2026-07-22T10:00:00Z"),
                Instant.parse("2026-07-22T10:00:01Z"),
                0,
                List.of(),
                new CollectionFailure(category, "Sanitized vendor failure.", retryAfterSeconds),
                Instant.parse("2026-07-22T10:01:01Z"));
    }
}
