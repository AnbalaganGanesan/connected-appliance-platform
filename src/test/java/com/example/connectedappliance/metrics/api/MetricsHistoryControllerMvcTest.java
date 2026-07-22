package com.example.connectedappliance.metrics.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.connectedappliance.appliance.api.error.ApplianceApiExceptionHandler;
import com.example.connectedappliance.appliance.application.exception.ApplianceNotFoundException;
import com.example.connectedappliance.bootstrap.UtcClockConfiguration;
import com.example.connectedappliance.metrics.application.history.InvalidTimeRangeException;
import com.example.connectedappliance.metrics.application.history.MetricsHistoryQueryService;
import com.example.connectedappliance.metrics.application.port.out.CollectionAttemptPage;
import com.example.connectedappliance.metrics.application.port.out.MetricSamplePage;
import com.example.connectedappliance.metrics.domain.CollectionAttempt;
import com.example.connectedappliance.metrics.domain.CollectionFailure;
import com.example.connectedappliance.metrics.domain.CollectionFailureCategory;
import com.example.connectedappliance.metrics.domain.CollectionOutcome;
import com.example.connectedappliance.metrics.domain.CollectionTrigger;
import com.example.connectedappliance.metrics.domain.CollectionWarning;
import com.example.connectedappliance.metrics.domain.MetricSample;
import com.example.connectedappliance.shared.error.ApiExceptionHandler;
import com.example.connectedappliance.shared.error.ProblemDetailFactory;
import com.example.connectedappliance.shared.error.ProblemResponseWriter;
import com.example.connectedappliance.shared.error.ValidationCodeMapper;
import com.example.connectedappliance.shared.error.ValidationErrorMapper;
import com.example.connectedappliance.shared.metric.CanonicalMetric;
import com.example.connectedappliance.shared.metric.CanonicalUnit;
import com.example.connectedappliance.shared.observability.CorrelationIdConstants;
import com.example.connectedappliance.shared.observability.CorrelationIdFilter;
import com.example.connectedappliance.shared.observability.CorrelationIdService;
import com.example.connectedappliance.shared.validation.UtcInstantQueryParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = MetricsHistoryController.class)
@Import({
        UtcClockConfiguration.class,
        CorrelationIdService.class,
        CorrelationIdFilter.class,
        ValidationCodeMapper.class,
        ValidationErrorMapper.class,
        ProblemDetailFactory.class,
        ProblemResponseWriter.class,
        ApiExceptionHandler.class,
        ApplianceApiExceptionHandler.class,
        CollectionAttemptApiMapper.class,
        MetricSampleApiMapper.class,
        UtcInstantQueryParser.class
})
class MetricsHistoryControllerMvcTest {

    private static final UUID APPLIANCE_ID =
            UUID.fromString("2f1b71b7-71a1-4b6c-9d68-54ed3bc24618");
    private static final UUID ATTEMPT_ID =
            UUID.fromString("8da9a201-85f4-4f8a-b9ec-a49f79f68361");
    private static final String ATTEMPT_PATH =
            "/api/v1/appliances/" + APPLIANCE_ID + "/collection-attempts";
    private static final String METRIC_PATH =
            "/api/v1/appliances/" + APPLIANCE_ID + "/metrics";
    private static final String FROM = "2026-07-21T10:00:00Z";
    private static final String TO = "2026-07-21T11:00:00Z";
    private static final String CORRELATION_ID = "task20-review";
    private static final String UUID_PATTERN =
            "[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private MetricsHistoryQueryService queryService;

    @Test
    void returnsAttemptHistoryWithDefaultsExactMappingWarningsFailureAndDueSnapshot()
            throws Exception {
        CollectionAttempt partial = partialAttempt();
        CollectionAttempt failed = failedAttempt();
        when(queryService.getCollectionAttempts(
                        APPLIANCE_ID, Optional.empty(), Optional.empty(), 0, 20))
                .thenReturn(new CollectionAttemptPage(List.of(partial, failed), 0, 20, 2, 1));

        var result = mockMvc.perform(get(ATTEMPT_PATH)
                        .header(CorrelationIdConstants.HEADER_NAME, CORRELATION_ID))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(header().string(CorrelationIdConstants.HEADER_NAME, CORRELATION_ID))
                .andExpect(jsonPath("$.items[0].id").value(partial.id().toString()))
                .andExpect(jsonPath("$.items[0].warnings[0].code").value("UNKNOWN_METRIC"))
                .andExpect(jsonPath("$.items[0].warnings[1].code").value("MALFORMED_VALUE"))
                .andExpect(jsonPath("$.items[0].failure").doesNotExist())
                .andExpect(jsonPath("$.items[0].nextCollectionDueAt")
                        .value("2026-07-21T10:02:00Z"))
                .andExpect(jsonPath("$.items[1].outcome").value("FAILED"))
                .andExpect(jsonPath("$.items[1].failure.category").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.items[1].failure.message").doesNotExist())
                .andExpect(jsonPath("$.items[1].failure.retryAfterSeconds").value(90))
                .andExpect(jsonPath("$.items[1].nextCollectionDueAt").doesNotExist())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andReturn();

        assertThat(objectMapper.readTree(result.getResponse().getContentAsString())
                        .path("items").get(0).properties().stream().map(java.util.Map.Entry::getKey))
                .containsExactly(
                        "id", "applianceId", "trigger", "outcome", "startedAt",
                        "completedAt", "sampleCount", "warnings", "failure",
                        "nextCollectionDueAt");
    }

    @ParameterizedTest
    @MethodSource("attemptFilterQueries")
    void delegatesEachExactAttemptFilterCombination(
            String triggerRaw,
            String outcomeRaw,
            Optional<CollectionTrigger> trigger,
            Optional<CollectionOutcome> outcome) throws Exception {
        when(queryService.getCollectionAttempts(APPLIANCE_ID, trigger, outcome, 1, 7))
                .thenReturn(new CollectionAttemptPage(List.of(), 1, 7, 0, 0));
        var request = get(ATTEMPT_PATH).queryParam("page", "1").queryParam("size", "7");
        if (triggerRaw != null) {
            request.queryParam("trigger", triggerRaw);
        }
        if (outcomeRaw != null) {
            request.queryParam("outcome", outcomeRaw);
        }

        mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(7));

        verify(queryService).getCollectionAttempts(APPLIANCE_ID, trigger, outcome, 1, 7);
    }

    @Test
    void preservesBeyondFinalAttemptPageMetadata() throws Exception {
        when(queryService.getCollectionAttempts(
                        APPLIANCE_ID, Optional.empty(), Optional.empty(), 5, 2))
                .thenReturn(new CollectionAttemptPage(List.of(), 5, 2, 3, 2));

        mockMvc.perform(get(ATTEMPT_PATH).queryParam("page", "5").queryParam("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.page").value(5))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @ParameterizedTest
    @ValueSource(strings = {"manual", "AUTOMATIC", "", "MANUAL,SCHEDULED"})
    void rejectsInvalidAttemptTriggerWithoutEchoingValue(String value) throws Exception {
        assertValidationFailure(ATTEMPT_PATH, "trigger", value, "INVALID_FORMAT");
        verifyNoInteractions(queryService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"success", "partial", "TIMEOUT", "", "SUCCESS,FAILED"})
    void rejectsInvalidAttemptOutcomeWithoutEchoingValue(String value) throws Exception {
        assertValidationFailure(ATTEMPT_PATH, "outcome", value, "INVALID_FORMAT");
        verifyNoInteractions(queryService);
    }

    @ParameterizedTest
    @MethodSource("invalidPagination")
    void rejectsInvalidAttemptPagination(String field, String value, String code)
            throws Exception {
        assertValidationFailure(ATTEMPT_PATH, field, value, code);
        verifyNoInteractions(queryService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"from", "failureCategory", "warningCode", "sort", "vendor"})
    void rejectsUnknownAttemptQueryParameter(String field) throws Exception {
        assertValidationFailure(ATTEMPT_PATH, field, "task20-sensitive-value", "UNKNOWN_FIELD");
        verifyNoInteractions(queryService);
    }

    @Test
    void returnsMetricHistoryWithRequiredRangeDefaultsAndExactDto() throws Exception {
        MetricSample first = metric(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                CanonicalMetric.TEMPERATURE,
                CanonicalUnit.CELSIUS,
                "-21.500000");
        MetricSample second = metric(
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                CanonicalMetric.POWER,
                CanonicalUnit.WATT,
                "125.000000");
        when(queryService.getMetricSamples(
                        APPLIANCE_ID, Instant.parse(FROM), Instant.parse(TO), 0, 20))
                .thenReturn(new MetricSamplePage(List.of(first, second), 0, 20, 2, 1));

        var result = mockMvc.perform(get(METRIC_PATH)
                        .queryParam("from", FROM)
                        .queryParam("to", TO)
                        .header(CorrelationIdConstants.HEADER_NAME, CORRELATION_ID))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(header().string(CorrelationIdConstants.HEADER_NAME, CORRELATION_ID))
                .andExpect(jsonPath("$.items[0].id").value(first.id().toString()))
                .andExpect(jsonPath("$.items[0].applianceId").value(APPLIANCE_ID.toString()))
                .andExpect(jsonPath("$.items[0].collectionAttemptId")
                        .value(ATTEMPT_ID.toString()))
                .andExpect(jsonPath("$.items[0].metricName").value("TEMPERATURE"))
                .andExpect(jsonPath("$.items[0].unit").value("CELSIUS"))
                .andExpect(jsonPath("$.items[0].value").isNumber())
                .andExpect(jsonPath("$.items[0].value").value(-21.5))
                .andExpect(jsonPath("$.items[0].observedAt")
                        .value("2026-07-21T10:01:00.123456Z"))
                .andExpect(jsonPath("$.items[0].ingestedAt")
                        .value("2026-07-21T10:01:01.123456Z"))
                .andExpect(jsonPath("$.items[1].id").value(second.id().toString()))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andReturn();

        assertThat(objectMapper.readTree(result.getResponse().getContentAsString())
                        .path("items").get(0).properties().stream().map(java.util.Map.Entry::getKey))
                .containsExactly(
                        "id", "applianceId", "collectionAttemptId", "metricName", "unit",
                        "value", "observedAt", "ingestedAt");
    }

    @Test
    void preservesCustomMetricPaginationAndEmptyBeyondFinalPage() throws Exception {
        when(queryService.getMetricSamples(
                        APPLIANCE_ID, Instant.parse(FROM), Instant.parse(TO), 4, 100))
                .thenReturn(new MetricSamplePage(List.of(), 4, 100, 3, 1));

        mockMvc.perform(get(METRIC_PATH)
                        .queryParam("from", FROM)
                        .queryParam("to", TO)
                        .queryParam("page", "4")
                        .queryParam("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.page").value(4))
                .andExpect(jsonPath("$.size").value(100))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @ParameterizedTest
    @MethodSource("invalidMetricTimes")
    void rejectsMissingMalformedAndNonUtcMetricTimestamps(
            String field, String value, boolean omit) throws Exception {
        var request = get(METRIC_PATH);
        if (!"from".equals(field) || !omit) {
            request.queryParam("from", "from".equals(field) ? value : FROM);
        }
        if (!"to".equals(field) || !omit) {
            request.queryParam("to", "to".equals(field) ? value : TO);
        }

        var result = mockMvc.perform(request.header(
                                CorrelationIdConstants.HEADER_NAME, CORRELATION_ID))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value(field))
                .andExpect(jsonPath("$.correlationId").value(CORRELATION_ID))
                .andExpect(jsonPath("$.timestamp").value(matchesPattern(".*Z")))
                .andReturn();

        if (value != null && !value.isBlank()) {
            assertThat(result.getResponse().getContentAsString()).doesNotContain(value);
        }
        verifyNoInteractions(queryService);
    }

    @ParameterizedTest
    @MethodSource("invalidRanges")
    void mapsEqualAndReversedMetricRangesToExactInvalidTimeRange(
            String from, String to) throws Exception {
        when(queryService.getMetricSamples(
                        APPLIANCE_ID, Instant.parse(from), Instant.parse(to), 0, 20))
                .thenThrow(new InvalidTimeRangeException());

        String response = mockMvc.perform(get(METRIC_PATH)
                        .queryParam("from", from)
                        .queryParam("to", to)
                        .header(CorrelationIdConstants.HEADER_NAME, CORRELATION_ID))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(
                        "urn:connected-appliance-platform:problem:invalid-time-range"))
                .andExpect(jsonPath("$.title").value("Invalid time range"))
                .andExpect(jsonPath("$.detail").value(
                        "The start of the time range must be before the end."))
                .andExpect(jsonPath("$.code").value("INVALID_TIME_RANGE"))
                .andExpect(jsonPath("$.instance").value(METRIC_PATH))
                .andExpect(jsonPath("$.correlationId").value(CORRELATION_ID))
                .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain(from, to, "DateTimeParseException");
    }

    @ParameterizedTest
    @MethodSource("invalidPagination")
    void rejectsInvalidMetricPagination(String field, String value, String code)
            throws Exception {
        var result = mockMvc.perform(get(METRIC_PATH)
                        .queryParam("from", FROM)
                        .queryParam("to", TO)
                        .queryParam(field, value))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value(field))
                .andExpect(jsonPath("$.errors[0].code").value(code))
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("rejectedValue");
        verifyNoInteractions(queryService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"metricName", "unit", "collectionAttemptId", "sort", "trigger"})
    void rejectsUnknownMetricQueryParameter(String field) throws Exception {
        var result = mockMvc.perform(get(METRIC_PATH)
                        .queryParam("from", FROM)
                        .queryParam("to", TO)
                        .queryParam(field, "task20-sensitive-value"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value(field))
                .andExpect(jsonPath("$.errors[0].code").value("UNKNOWN_FIELD"))
                .andReturn();
        assertThat(result.getResponse().getContentAsString())
                .doesNotContain("task20-sensitive-value");
        verifyNoInteractions(queryService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"collection-attempts", "metrics"})
    void mapsMalformedUuidBeforeQueryServiceInvocation(String suffix) throws Exception {
        var request = get("/api/v1/appliances/not-a-uuid/" + suffix);
        if ("metrics".equals(suffix)) {
            request.queryParam("from", FROM).queryParam("to", TO);
        }
        mockMvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("applianceId"));
        verifyNoInteractions(queryService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"collection-attempts", "metrics"})
    void mapsMissingApplianceThroughExistingNotFoundContract(String suffix) throws Exception {
        if ("metrics".equals(suffix)) {
            when(queryService.getMetricSamples(
                            APPLIANCE_ID, Instant.parse(FROM), Instant.parse(TO), 0, 20))
                    .thenThrow(new ApplianceNotFoundException());
        } else {
            when(queryService.getCollectionAttempts(
                            APPLIANCE_ID, Optional.empty(), Optional.empty(), 0, 20))
                    .thenThrow(new ApplianceNotFoundException());
        }
        var request = get("/api/v1/appliances/" + APPLIANCE_ID + "/" + suffix)
                .header(CorrelationIdConstants.HEADER_NAME, CORRELATION_ID);
        if ("metrics".equals(suffix)) {
            request.queryParam("from", FROM).queryParam("to", TO);
        }
        mockMvc.perform(request)
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("APPLIANCE_NOT_FOUND"))
                .andExpect(jsonPath("$.correlationId").value(CORRELATION_ID));
    }

    @ParameterizedTest
    @ValueSource(strings = {"collection-attempts", "metrics"})
    void rejectsUnacceptableResponseMediaBeforeQueryServiceInvocation(String suffix)
            throws Exception {
        var request = get("/api/v1/appliances/" + APPLIANCE_ID + "/" + suffix)
                .accept(MediaType.APPLICATION_XML);
        if ("metrics".equals(suffix)) {
            request.queryParam("from", FROM).queryParam("to", TO);
        }
        mockMvc.perform(request)
                .andExpect(status().isNotAcceptable())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("NOT_ACCEPTABLE"));
        verifyNoInteractions(queryService);
    }

    @Test
    void generatesCorrelationForSuccessAndRejectsInvalidCorrelationBeforeService() throws Exception {
        when(queryService.getCollectionAttempts(
                        APPLIANCE_ID, Optional.empty(), Optional.empty(), 0, 20))
                .thenReturn(new CollectionAttemptPage(List.of(), 0, 20, 0, 0));
        mockMvc.perform(get(ATTEMPT_PATH))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        CorrelationIdConstants.HEADER_NAME, matchesPattern(UUID_PATTERN)));

        String invalid = "invalid correlation value";
        var result = mockMvc.perform(get(METRIC_PATH)
                        .queryParam("from", FROM)
                        .queryParam("to", TO)
                        .header(CorrelationIdConstants.HEADER_NAME, invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CORRELATION_ID"))
                .andExpect(header().string(
                        CorrelationIdConstants.HEADER_NAME, matchesPattern(UUID_PATTERN)))
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain(invalid);
        verify(queryService).getCollectionAttempts(
                APPLIANCE_ID, Optional.empty(), Optional.empty(), 0, 20);
    }

    private void assertValidationFailure(
            String path, String field, String value, String code) throws Exception {
        var result = mockMvc.perform(get(path)
                        .queryParam(field, value)
                        .header(CorrelationIdConstants.HEADER_NAME, CORRELATION_ID))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value(field))
                .andExpect(jsonPath("$.errors[0].code").value(code))
                .andExpect(jsonPath("$.instance").value(path))
                .andExpect(jsonPath("$.correlationId").value(CORRELATION_ID))
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("rejectedValue");
    }

    private CollectionAttempt partialAttempt() {
        return new CollectionAttempt(
                ATTEMPT_ID,
                APPLIANCE_ID,
                CollectionTrigger.MANUAL,
                CollectionOutcome.PARTIAL_SUCCESS,
                Instant.parse("2026-07-21T10:01:00Z"),
                Instant.parse("2026-07-21T10:01:01Z"),
                1,
                List.of(
                        new CollectionWarning("UNKNOWN_METRIC", "Unknown metric."),
                        new CollectionWarning("MALFORMED_VALUE", "Malformed value.")),
                null,
                Instant.parse("2026-07-21T10:02:00Z"));
    }

    private CollectionAttempt failedAttempt() {
        return new CollectionAttempt(
                UUID.fromString("8da9a201-85f4-4f8a-b9ec-a49f79f68362"),
                APPLIANCE_ID,
                CollectionTrigger.SCHEDULED,
                CollectionOutcome.FAILED,
                Instant.parse("2026-07-21T09:01:00Z"),
                Instant.parse("2026-07-21T09:01:01Z"),
                0,
                List.of(),
                new CollectionFailure(CollectionFailureCategory.RATE_LIMITED, null, 90),
                null);
    }

    private MetricSample metric(
            UUID id, CanonicalMetric metric, CanonicalUnit unit, String value) {
        return new MetricSample(
                id,
                APPLIANCE_ID,
                ATTEMPT_ID,
                metric,
                unit,
                new BigDecimal(value),
                Instant.parse("2026-07-21T10:01:00.123456Z"),
                Instant.parse("2026-07-21T10:01:01.123456Z"));
    }

    private static Stream<Arguments> attemptFilterQueries() {
        return Stream.of(
                Arguments.of(null, null, Optional.empty(), Optional.empty()),
                Arguments.of("MANUAL", null, Optional.of(CollectionTrigger.MANUAL), Optional.empty()),
                Arguments.of(null, "SUCCESS", Optional.empty(), Optional.of(CollectionOutcome.SUCCESS)),
                Arguments.of(
                        "SCHEDULED",
                        "FAILED",
                        Optional.of(CollectionTrigger.SCHEDULED),
                        Optional.of(CollectionOutcome.FAILED)));
    }

    private static Stream<Arguments> invalidPagination() {
        return Stream.of(
                Arguments.of("page", "-1", "OUT_OF_RANGE"),
                Arguments.of("page", "", "INVALID_FORMAT"),
                Arguments.of("page", "one", "INVALID_FORMAT"),
                Arguments.of("size", "0", "OUT_OF_RANGE"),
                Arguments.of("size", "-1", "OUT_OF_RANGE"),
                Arguments.of("size", "101", "OUT_OF_RANGE"),
                Arguments.of("size", "many", "INVALID_FORMAT"));
    }

    private static Stream<Arguments> invalidMetricTimes() {
        return Stream.of(
                Arguments.of("from", null, true),
                Arguments.of("to", null, true),
                Arguments.of("from", "", false),
                Arguments.of("to", " ", false),
                Arguments.of("from", "not-a-timestamp", false),
                Arguments.of("to", "2026-07-21T10:00:00", false),
                Arguments.of("from", "2026-07-21T10:00:00+00:00", false),
                Arguments.of("to", "2026-07-21T15:30:00+05:30", false),
                Arguments.of("from", "2026-07-21T10:00:00z", false));
    }

    private static Stream<Arguments> invalidRanges() {
        return Stream.of(
                Arguments.of(FROM, FROM),
                Arguments.of(TO, FROM));
    }
}
