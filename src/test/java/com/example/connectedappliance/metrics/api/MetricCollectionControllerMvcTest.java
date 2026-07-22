package com.example.connectedappliance.metrics.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.connectedappliance.appliance.api.error.ApplianceApiExceptionHandler;
import com.example.connectedappliance.appliance.application.exception.ApplianceNotFoundException;
import com.example.connectedappliance.bootstrap.UtcClockConfiguration;
import com.example.connectedappliance.metrics.application.collectnow.AppliancePausedException;
import com.example.connectedappliance.metrics.application.collectnow.CollectNowService;
import com.example.connectedappliance.metrics.application.collectnow.CollectionAlreadyInProgressException;
import com.example.connectedappliance.metrics.application.collectnow.CollectionServiceUnavailableException;
import com.example.connectedappliance.metrics.domain.CollectionAttempt;
import com.example.connectedappliance.metrics.domain.CollectionFailure;
import com.example.connectedappliance.metrics.domain.CollectionFailureCategory;
import com.example.connectedappliance.metrics.domain.CollectionOutcome;
import com.example.connectedappliance.metrics.domain.CollectionTrigger;
import com.example.connectedappliance.metrics.domain.CollectionWarning;
import com.example.connectedappliance.shared.error.ApiExceptionHandler;
import com.example.connectedappliance.shared.error.ProblemDetailFactory;
import com.example.connectedappliance.shared.error.ProblemResponseWriter;
import com.example.connectedappliance.shared.error.ValidationCodeMapper;
import com.example.connectedappliance.shared.error.ValidationErrorMapper;
import com.example.connectedappliance.shared.observability.CorrelationIdConstants;
import com.example.connectedappliance.shared.observability.CorrelationIdFilter;
import com.example.connectedappliance.shared.observability.CorrelationIdService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = MetricCollectionController.class)
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
        CollectionAttemptApiMapper.class
})
class MetricCollectionControllerMvcTest {

    private static final UUID APPLIANCE_ID =
            UUID.fromString("2f1b71b7-71a1-4b6c-9d68-54ed3bc24618");
    private static final UUID ATTEMPT_ID =
            UUID.fromString("8da9a201-85f4-4f8a-b9ec-a49f79f68361");
    private static final String PATH =
            "/api/v1/appliances/" + APPLIANCE_ID + "/actions/collect-now";
    private static final String CORRELATION_ID = "task19-review";
    private static final String UUID_PATTERN =
            "[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CollectNowService collectNowService;

    @Test
    void returnsExactSuccessDtoForBodylessPostWithoutContentType() throws Exception {
        when(collectNowService.collectNow(APPLIANCE_ID)).thenReturn(successAttempt());

        var result = mockMvc.perform(post(PATH)
                        .header(CorrelationIdConstants.HEADER_NAME, CORRELATION_ID))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(header().string(CorrelationIdConstants.HEADER_NAME, CORRELATION_ID))
                .andExpect(jsonPath("$.id").value(ATTEMPT_ID.toString()))
                .andExpect(jsonPath("$.applianceId").value(APPLIANCE_ID.toString()))
                .andExpect(jsonPath("$.trigger").value("MANUAL"))
                .andExpect(jsonPath("$.outcome").value("SUCCESS"))
                .andExpect(jsonPath("$.startedAt").value("2026-07-22T10:00:00Z"))
                .andExpect(jsonPath("$.completedAt").value("2026-07-22T10:00:01Z"))
                .andExpect(jsonPath("$.sampleCount").value(2))
                .andExpect(jsonPath("$.warnings").isEmpty())
                .andExpect(jsonPath("$.failure").doesNotExist())
                .andExpect(jsonPath("$.nextCollectionDueAt").value("2026-07-22T10:00:31Z"))
                .andExpect(jsonPath("$.correlationId").doesNotExist())
                .andReturn();

        assertThat(objectMapper.readTree(result.getResponse().getContentAsString()).properties()
                        .stream()
                        .map(java.util.Map.Entry::getKey))
                .containsExactly(
                        "id", "applianceId", "trigger", "outcome", "startedAt",
                        "completedAt", "sampleCount", "warnings", "failure",
                        "nextCollectionDueAt");
    }

    @Test
    void generatesCorrelationIdForBodylessSuccessWhenMissing() throws Exception {
        when(collectNowService.collectNow(APPLIANCE_ID)).thenReturn(successAttempt());

        mockMvc.perform(post(PATH))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        CorrelationIdConstants.HEADER_NAME, matchesPattern(UUID_PATTERN)));
    }

    @Test
    void returnsPartialSuccessWithOrderedWarningsAndNullFailure() throws Exception {
        when(collectNowService.collectNow(APPLIANCE_ID)).thenReturn(new CollectionAttempt(
                ATTEMPT_ID,
                APPLIANCE_ID,
                CollectionTrigger.MANUAL,
                CollectionOutcome.PARTIAL_SUCCESS,
                started(),
                completed(),
                1,
                List.of(
                        new CollectionWarning("UNKNOWN_METRIC", "Unknown metric."),
                        new CollectionWarning("MALFORMED_VALUE", "Malformed value.")),
                null,
                due()));

        mockMvc.perform(post(PATH).header(CorrelationIdConstants.HEADER_NAME, CORRELATION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("PARTIAL_SUCCESS"))
                .andExpect(jsonPath("$.sampleCount").value(1))
                .andExpect(jsonPath("$.warnings[0].code").value("UNKNOWN_METRIC"))
                .andExpect(jsonPath("$.warnings[1].code").value("MALFORMED_VALUE"))
                .andExpect(jsonPath("$.failure").doesNotExist());
    }

    @Test
    void returnsPersistedTimeoutAsHttp200RatherThanGatewayTimeout() throws Exception {
        when(collectNowService.collectNow(APPLIANCE_ID))
                .thenReturn(failedAttempt(CollectionFailureCategory.TIMEOUT, null, List.of()));

        mockMvc.perform(post(PATH).header(CorrelationIdConstants.HEADER_NAME, CORRELATION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("FAILED"))
                .andExpect(jsonPath("$.sampleCount").value(0))
                .andExpect(jsonPath("$.failure.category").value("TIMEOUT"))
                .andExpect(jsonPath("$.failure.retryAfterSeconds").doesNotExist());
    }

    @Test
    void returnsPersistedRateLimitAsHttp200WithRetryAfterInBodyOnly() throws Exception {
        when(collectNowService.collectNow(APPLIANCE_ID))
                .thenReturn(failedAttempt(CollectionFailureCategory.RATE_LIMITED, 90, List.of()));

        mockMvc.perform(post(PATH).header(CorrelationIdConstants.HEADER_NAME, CORRELATION_ID))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Retry-After"))
                .andExpect(jsonPath("$.failure.category").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.failure.retryAfterSeconds").value(90));
    }

    @Test
    void returnsPersistedInvalidDataAsHttp200WithOrderedWarnings() throws Exception {
        when(collectNowService.collectNow(APPLIANCE_ID)).thenReturn(failedAttempt(
                CollectionFailureCategory.INVALID_DATA,
                null,
                List.of(
                        new CollectionWarning("UNKNOWN_METRIC", "Unknown metric."),
                        new CollectionWarning("MALFORMED_VALUE", "Malformed value."))));

        mockMvc.perform(post(PATH).header(CorrelationIdConstants.HEADER_NAME, CORRELATION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.failure.category").value("INVALID_DATA"))
                .andExpect(jsonPath("$.warnings[0].code").value("UNKNOWN_METRIC"))
                .andExpect(jsonPath("$.warnings[1].code").value("MALFORMED_VALUE"))
                .andExpect(jsonPath("$.samples").doesNotExist());
    }

    @Test
    void returnsPersistedUnexpectedFailureAsSanitizedHttp200() throws Exception {
        when(collectNowService.collectNow(APPLIANCE_ID))
                .thenReturn(failedAttempt(CollectionFailureCategory.UNEXPECTED, null, List.of()));

        String body = mockMvc.perform(post(PATH)
                        .header(CorrelationIdConstants.HEADER_NAME, CORRELATION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.failure.category").value("UNEXPECTED"))
                .andExpect(jsonPath("$.failure.message")
                        .value("The vendor request failed unexpectedly."))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("SQLException", "password", "vendor-client");
    }

    @Test
    void mapsMissingApplianceToApprovedNotFoundProblem() throws Exception {
        when(collectNowService.collectNow(APPLIANCE_ID)).thenThrow(new ApplianceNotFoundException());

        assertProblem(mockMvc.perform(post(PATH)
                        .header(CorrelationIdConstants.HEADER_NAME, CORRELATION_ID))
                        .andExpect(status().isNotFound())
                        .andReturn().getResponse().getContentAsString(),
                404,
                "APPLIANCE_NOT_FOUND",
                "appliance-not-found",
                "Appliance not found",
                "No appliance exists with the supplied identifier.");
    }

    @Test
    void mapsPausedToApprovedConflictProblem() throws Exception {
        when(collectNowService.collectNow(APPLIANCE_ID)).thenThrow(new AppliancePausedException());

        assertProblem(mockMvc.perform(post(PATH)
                        .header(CorrelationIdConstants.HEADER_NAME, CORRELATION_ID))
                        .andExpect(status().isConflict())
                        .andReturn().getResponse().getContentAsString(),
                409,
                "APPLIANCE_PAUSED",
                "appliance-paused",
                "Appliance is paused",
                "The appliance is paused and cannot start collection.");
    }

    @Test
    void mapsBusyToApprovedConflictProblem() throws Exception {
        when(collectNowService.collectNow(APPLIANCE_ID))
                .thenThrow(new CollectionAlreadyInProgressException());

        assertProblem(mockMvc.perform(post(PATH)
                        .header(CorrelationIdConstants.HEADER_NAME, CORRELATION_ID))
                        .andExpect(status().isConflict())
                        .andReturn().getResponse().getContentAsString(),
                409,
                "COLLECTION_ALREADY_IN_PROGRESS",
                "collection-already-in-progress",
                "Collection already in progress",
                "A collection is already in progress for the appliance.");
    }

    @Test
    void mapsSaturationToSanitizedServiceUnavailableProblem() throws Exception {
        when(collectNowService.collectNow(APPLIANCE_ID))
                .thenThrow(new CollectionServiceUnavailableException());

        var result = mockMvc.perform(post(PATH)
                        .header(CorrelationIdConstants.HEADER_NAME, CORRELATION_ID))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().doesNotExist("Retry-After"))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertProblem(
                body,
                503,
                "SERVICE_UNAVAILABLE",
                "service-unavailable",
                "Service unavailable",
                "The collection service is temporarily unavailable.");
        assertThat(body).doesNotContain("executor", "queue", "attemptId", "vendor");
    }

    @Test
    void rejectsMalformedUuidBeforeServiceInvocation() throws Exception {
        mockMvc.perform(post("/api/v1/appliances/not-a-uuid/actions/collect-now")
                        .header(CorrelationIdConstants.HEADER_NAME, CORRELATION_ID))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("applianceId"))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_FORMAT"));

        verifyNoInteractions(collectNowService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"force", "async", "vendor", "timeout", "trigger", "arbitrary"})
    void rejectsEveryQueryParameterWithoutEchoingItsValue(String parameter) throws Exception {
        String sensitiveValue = "task19-sensitive-query-value";
        var result = mockMvc.perform(post(PATH)
                        .header(CorrelationIdConstants.HEADER_NAME, CORRELATION_ID)
                        .queryParam(parameter, sensitiveValue))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value(parameter))
                .andExpect(jsonPath("$.errors[0].code").value("UNKNOWN_FIELD"))
                .andExpect(jsonPath("$.instance").value(PATH))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain(sensitiveValue);
        verifyNoInteractions(collectNowService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"force\":true}"})
    void rejectsEverySyntacticallyValidJsonBody(String body) throws Exception {
        String response = mockMvc.perform(post(PATH)
                        .header(CorrelationIdConstants.HEADER_NAME, CORRELATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("body"))
                .andExpect(jsonPath("$.errors[0].code").value("UNKNOWN_FIELD"))
                .andExpect(jsonPath("$.errors[0].message").value("request body is not supported"))
                .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain("force", "true");
        verifyNoInteractions(collectNowService);
    }

    @Test
    void mapsMalformedJsonBodyToMalformedJsonProblem() throws Exception {
        mockMvc.perform(post(PATH)
                        .header(CorrelationIdConstants.HEADER_NAME, CORRELATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("MALFORMED_JSON"))
                .andExpect(jsonPath("$.detail").value("The request body contains malformed JSON."));

        verifyNoInteractions(collectNowService);
    }

    @Test
    void rejectsInvalidCorrelationBeforeServiceInvocation() throws Exception {
        String invalid = "invalid correlation value";
        var result = mockMvc.perform(post(PATH)
                        .header(CorrelationIdConstants.HEADER_NAME, invalid))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_CORRELATION_ID"))
                .andExpect(header().string(
                        CorrelationIdConstants.HEADER_NAME, matchesPattern(UUID_PATTERN)))
                .andReturn();

        var json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.path("correlationId").asText())
                .isEqualTo(result.getResponse().getHeader(CorrelationIdConstants.HEADER_NAME));
        assertThat(result.getResponse().getContentAsString()).doesNotContain(invalid);
        verifyNoInteractions(collectNowService);
    }

    @Test
    void rejectsUnacceptableResponseMediaBeforeServiceInvocation() throws Exception {
        mockMvc.perform(post(PATH)
                        .header(CorrelationIdConstants.HEADER_NAME, CORRELATION_ID)
                        .accept(MediaType.APPLICATION_XML))
                .andExpect(status().isNotAcceptable())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("NOT_ACCEPTABLE"));

        verifyNoInteractions(collectNowService);
    }

    @Test
    void rejectsUnsupportedNonEmptyBodyMediaBeforeServiceInvocation() throws Exception {
        mockMvc.perform(post(PATH)
                        .header(CorrelationIdConstants.HEADER_NAME, CORRELATION_ID)
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("unsupported body"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));

        verifyNoInteractions(collectNowService);
    }

    private void assertProblem(
            String body,
            int expectedStatus,
            String expectedCode,
            String expectedTypeSuffix,
            String expectedTitle,
            String expectedDetail) throws Exception {
        var json = objectMapper.readTree(body);
        assertThat(json.path("type").asText())
                .isEqualTo("urn:connected-appliance-platform:problem:" + expectedTypeSuffix);
        assertThat(json.path("title").asText()).isEqualTo(expectedTitle);
        assertThat(json.path("status").asInt()).isEqualTo(expectedStatus);
        assertThat(json.path("detail").asText()).isEqualTo(expectedDetail);
        assertThat(json.path("instance").asText()).isEqualTo(PATH);
        assertThat(json.path("code").asText()).isEqualTo(expectedCode);
        assertThat(json.path("correlationId").asText()).isEqualTo(CORRELATION_ID);
        assertThat(json.path("timestamp").asText()).endsWith("Z");
        assertThat(body).doesNotContain(
                "stackTrace", "SQLException", "password", "vendor-client", "executorSize");
    }

    private CollectionAttempt successAttempt() {
        return new CollectionAttempt(
                ATTEMPT_ID,
                APPLIANCE_ID,
                CollectionTrigger.MANUAL,
                CollectionOutcome.SUCCESS,
                started(),
                completed(),
                2,
                List.of(),
                null,
                due());
    }

    private CollectionAttempt failedAttempt(
            CollectionFailureCategory category,
            Integer retryAfterSeconds,
            List<CollectionWarning> warnings) {
        String message = category == CollectionFailureCategory.UNEXPECTED
                ? "The vendor request failed unexpectedly."
                : "The vendor request could not be completed.";
        return new CollectionAttempt(
                ATTEMPT_ID,
                APPLIANCE_ID,
                CollectionTrigger.MANUAL,
                CollectionOutcome.FAILED,
                started(),
                completed(),
                0,
                warnings,
                new CollectionFailure(category, message, retryAfterSeconds),
                due());
    }

    private Instant started() {
        return Instant.parse("2026-07-22T10:00:00Z");
    }

    private Instant completed() {
        return Instant.parse("2026-07-22T10:00:01Z");
    }

    private Instant due() {
        return Instant.parse("2026-07-22T10:00:31Z");
    }
}
