package com.example.connectedappliance.metrics.application.history;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.example.connectedappliance.appliance.application.exception.ApplianceNotFoundException;
import com.example.connectedappliance.appliance.application.port.in.ApplianceExistenceQueryPort;
import com.example.connectedappliance.metrics.application.port.out.CollectionAttemptPage;
import com.example.connectedappliance.metrics.application.port.out.CollectionAttemptQuery;
import com.example.connectedappliance.metrics.application.port.out.HistoryPageRequest;
import com.example.connectedappliance.metrics.application.port.out.MetricSamplePage;
import com.example.connectedappliance.metrics.application.port.out.MetricSampleQuery;
import com.example.connectedappliance.metrics.application.port.out.MetricsRepository;
import com.example.connectedappliance.metrics.domain.CollectionOutcome;
import com.example.connectedappliance.metrics.domain.CollectionTrigger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetricsHistoryQueryServiceTest {

    private static final UUID APPLIANCE_ID =
            UUID.fromString("2f1b71b7-71a1-4b6c-9d68-54ed3bc24618");
    private static final Instant FROM = Instant.parse("2026-07-21T10:00:00Z");
    private static final Instant TO = Instant.parse("2026-07-21T11:00:00Z");

    @Mock
    private ApplianceExistenceQueryPort applianceExistence;

    @Mock
    private MetricsRepository metricsRepository;

    @ParameterizedTest
    @MethodSource("attemptFilters")
    void delegatesEachAttemptFilterCombinationExactlyOnce(
            Optional<CollectionTrigger> trigger, Optional<CollectionOutcome> outcome) {
        CollectionAttemptPage expected = new CollectionAttemptPage(List.of(), 2, 7, 0, 0);
        CollectionAttemptQuery query = new CollectionAttemptQuery(
                APPLIANCE_ID, trigger, outcome, new HistoryPageRequest(2, 7));
        when(applianceExistence.exists(APPLIANCE_ID)).thenReturn(true);
        when(metricsRepository.findAttempts(query)).thenReturn(expected);
        MetricsHistoryQueryService service = service();

        assertThat(service.getCollectionAttempts(APPLIANCE_ID, trigger, outcome, 2, 7))
                .isSameAs(expected);

        verify(applianceExistence).exists(APPLIANCE_ID);
        verify(metricsRepository).findAttempts(query);
    }

    @Test
    void treatsPausedApplianceAsExistingWithoutInspectingCollectionState() {
        CollectionAttemptPage expected = new CollectionAttemptPage(List.of(), 0, 20, 0, 0);
        when(applianceExistence.exists(APPLIANCE_ID)).thenReturn(true);
        when(metricsRepository.findAttempts(new CollectionAttemptQuery(
                        APPLIANCE_ID,
                        Optional.empty(),
                        Optional.empty(),
                        new HistoryPageRequest(0, 20))))
                .thenReturn(expected);

        assertThat(service().getCollectionAttempts(
                        APPLIANCE_ID, Optional.empty(), Optional.empty(), 0, 20))
                .isSameAs(expected);
    }

    @Test
    void rejectsMissingApplianceBeforeAttemptRepositoryQuery() {
        when(applianceExistence.exists(APPLIANCE_ID)).thenReturn(false);

        assertThatThrownBy(() -> service().getCollectionAttempts(
                        APPLIANCE_ID, Optional.empty(), Optional.empty(), 0, 20))
                .isInstanceOf(ApplianceNotFoundException.class);

        verifyNoInteractions(metricsRepository);
    }

    @Test
    void delegatesExactMetricRangeAndPaginationOnceWithoutDurationLimit() {
        Instant longRangeEnd = FROM.plusSeconds(40L * 24 * 60 * 60);
        MetricSamplePage expected = new MetricSamplePage(List.of(), 3, 100, 0, 0);
        MetricSampleQuery query = new MetricSampleQuery(
                APPLIANCE_ID, FROM, longRangeEnd, new HistoryPageRequest(3, 100));
        when(applianceExistence.exists(APPLIANCE_ID)).thenReturn(true);
        when(metricsRepository.findMetricSamples(query)).thenReturn(expected);

        assertThat(service().getMetricSamples(APPLIANCE_ID, FROM, longRangeEnd, 3, 100))
                .isSameAs(expected);

        verify(applianceExistence).exists(APPLIANCE_ID);
        verify(metricsRepository).findMetricSamples(query);
    }

    @Test
    void rejectsMissingApplianceBeforeMetricRepositoryQuery() {
        when(applianceExistence.exists(APPLIANCE_ID)).thenReturn(false);

        assertThatThrownBy(() -> service().getMetricSamples(APPLIANCE_ID, FROM, TO, 0, 20))
                .isInstanceOf(ApplianceNotFoundException.class);

        verifyNoInteractions(metricsRepository);
    }

    @ParameterizedTest
    @MethodSource("invalidRanges")
    void rejectsEqualAndReversedRangesBeforeExistenceOrRepositoryQuery(
            Instant from, Instant to) {
        assertThatThrownBy(() -> service().getMetricSamples(APPLIANCE_ID, from, to, 0, 20))
                .isInstanceOf(InvalidTimeRangeException.class)
                .hasMessage("INVALID_TIME_RANGE");

        verifyNoInteractions(applianceExistence, metricsRepository);
    }

    @Test
    void rejectsNullInputsBeforeUsingPorts() {
        MetricsHistoryQueryService service = service();

        assertThatNullPointerException().isThrownBy(() -> service.getCollectionAttempts(
                null, Optional.empty(), Optional.empty(), 0, 20));
        assertThatNullPointerException().isThrownBy(() -> service.getCollectionAttempts(
                APPLIANCE_ID, null, Optional.empty(), 0, 20));
        assertThatNullPointerException().isThrownBy(() -> service.getCollectionAttempts(
                APPLIANCE_ID, Optional.empty(), null, 0, 20));
        assertThatNullPointerException().isThrownBy(() -> service.getMetricSamples(
                APPLIANCE_ID, null, TO, 0, 20));
        assertThatNullPointerException().isThrownBy(() -> service.getMetricSamples(
                APPLIANCE_ID, FROM, null, 0, 20));

        verifyNoInteractions(applianceExistence, metricsRepository);
    }

    private MetricsHistoryQueryService service() {
        return new MetricsHistoryQueryService(applianceExistence, metricsRepository);
    }

    private static java.util.stream.Stream<Arguments> attemptFilters() {
        return java.util.stream.Stream.of(
                Arguments.of(Optional.empty(), Optional.empty()),
                Arguments.of(Optional.of(CollectionTrigger.MANUAL), Optional.empty()),
                Arguments.of(Optional.empty(), Optional.of(CollectionOutcome.SUCCESS)),
                Arguments.of(
                        Optional.of(CollectionTrigger.SCHEDULED),
                        Optional.of(CollectionOutcome.FAILED)));
    }

    private static java.util.stream.Stream<Arguments> invalidRanges() {
        return java.util.stream.Stream.of(
                Arguments.of(FROM, FROM),
                Arguments.of(TO, FROM));
    }
}
