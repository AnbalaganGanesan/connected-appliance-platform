package com.example.connectedappliance.shared.error;

import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.example.connectedappliance.bootstrap.UtcClockConfiguration;
import com.example.connectedappliance.shared.observability.CorrelationIdConstants;
import com.example.connectedappliance.shared.observability.CorrelationIdFilter;
import com.example.connectedappliance.shared.observability.CorrelationIdService;
import com.example.task5fixture.Task5FixtureController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = Task5FixtureController.class)
@Import({
        UtcClockConfiguration.class,
        CorrelationIdService.class,
        CorrelationIdFilter.class,
        ValidationCodeMapper.class,
        ValidationErrorMapper.class,
        ProblemDetailFactory.class,
        ProblemResponseWriter.class,
        ApiExceptionHandler.class,
        Task5FixtureController.class
})
class SharedHttpContractMvcTest {

    private static final String UUID_PATTERN =
            "[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}";
    private static final String TIMESTAMP_PATTERN =
            "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?Z";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void resetFixture() {
        Task5FixtureController.resetInvocations();
        MDC.remove(CorrelationIdConstants.MDC_KEY);
    }

    @AfterEach
    void verifyMdcWasCleared() {
        assertThat(MDC.get(CorrelationIdConstants.MDC_KEY)).isNull();
    }

    @Test
    void acceptsValidSuppliedCorrelationIdUnchangedDuringRequest() throws Exception {
        String correlationId = "review.request-42_alpha";

        mockMvc.perform(get("/test/task5/success")
                        .header(CorrelationIdConstants.HEADER_NAME, correlationId))
                .andExpect(status().isOk())
                .andExpect(header().string(CorrelationIdConstants.HEADER_NAME, correlationId))
                .andExpect(jsonPath("$.correlationId").value(correlationId))
                .andExpect(jsonPath("$.requestAttribute").value(correlationId));
    }

    @Test
    void acceptsSixtyFourCharacterCorrelationId() throws Exception {
        String correlationId = "A".repeat(64);

        mockMvc.perform(get("/test/task5/success")
                        .header(CorrelationIdConstants.HEADER_NAME, correlationId))
                .andExpect(status().isOk())
                .andExpect(header().string(CorrelationIdConstants.HEADER_NAME, correlationId))
                .andExpect(jsonPath("$.correlationId").value(correlationId));
    }

    @Test
    void generatesCanonicalLowercaseUuidWhenCorrelationIdIsMissing() throws Exception {
        var result = mockMvc.perform(get("/test/task5/success"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        CorrelationIdConstants.HEADER_NAME,
                        matchesPattern(UUID_PATTERN)))
                .andReturn();

        String generated = result.getResponse()
                .getHeader(CorrelationIdConstants.HEADER_NAME);
        var responseBody = objectMapper.readTree(result.getResponse().getContentAsString());

        assertThat(generated).isNotNull();
        assertThat(UUID.fromString(generated).toString()).isEqualTo(generated);
        assertThat(responseBody.path("correlationId").asText()).isEqualTo(generated);
        assertThat(responseBody.path("requestAttribute").asText()).isEqualTo(generated);
    }

    @Test
    void rejectsBlankCorrelationIdWithSafeGeneratedId() throws Exception {
        var result = mockMvc.perform(get("/test/task5/success")
                        .header(CorrelationIdConstants.HEADER_NAME, " "))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string(
                        CorrelationIdConstants.HEADER_NAME,
                        matchesPattern(UUID_PATTERN)))
                .andExpect(jsonPath("$.code").value("INVALID_CORRELATION_ID"))
                .andExpect(jsonPath("$.correlationId", matchesPattern(UUID_PATTERN)))
                .andExpect(jsonPath("$.instance").value("/test/task5/success"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        String headerCorrelationId = result.getResponse()
                .getHeader(CorrelationIdConstants.HEADER_NAME);
        String bodyCorrelationId = objectMapper.readTree(body)
                .path("correlationId")
                .asText();

        assertThat(body).doesNotContain("\"correlationId\":\" \"");
        assertThat(bodyCorrelationId).isEqualTo(headerCorrelationId);
        assertThat(Task5FixtureController.invocations()).isZero();
    }

    @Test
    void rejectsEmptyCorrelationId() throws Exception {
        mockMvc.perform(get("/test/task5/success")
                        .header(CorrelationIdConstants.HEADER_NAME, ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CORRELATION_ID"))
                .andExpect(header().string(
                        CorrelationIdConstants.HEADER_NAME,
                        matchesPattern(UUID_PATTERN)));

        assertThat(Task5FixtureController.invocations()).isZero();
    }

    @ParameterizedTest
    @MethodSource("invalidCorrelationIds")
    void rejectsMalformedCorrelationIds(String suppliedId) throws Exception {
        mockMvc.perform(get("/test/task5/success")
                        .header(CorrelationIdConstants.HEADER_NAME, suppliedId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CORRELATION_ID"))
                .andExpect(header().string(
                        CorrelationIdConstants.HEADER_NAME,
                        matchesPattern(UUID_PATTERN)))
                .andExpect(content().string(not(containsString(suppliedId))));
    }

    @Test
    void rejectsRepeatedCorrelationIdWithoutInvokingController() throws Exception {
        String first = "first-request";
        String second = "second-request";

        mockMvc.perform(get("/test/task5/success")
                        .header(CorrelationIdConstants.HEADER_NAME, first, second))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CORRELATION_ID"))
                .andExpect(header().string(
                        CorrelationIdConstants.HEADER_NAME,
                        matchesPattern(UUID_PATTERN)))
                .andExpect(content().string(not(containsString(first))))
                .andExpect(content().string(not(containsString(second))));

        assertThat(Task5FixtureController.invocations()).isZero();
    }

    @Test
    void rejectsOverlongCorrelationId() throws Exception {
        mockMvc.perform(get("/test/task5/success")
                        .header(CorrelationIdConstants.HEADER_NAME, "a".repeat(65)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CORRELATION_ID"));
    }

    @Test
    void doesNotLeakCorrelationIdBetweenRequests() throws Exception {
        mockMvc.perform(get("/test/task5/success")
                        .header(CorrelationIdConstants.HEADER_NAME, "first-request"))
                .andExpect(jsonPath("$.correlationId").value("first-request"));

        mockMvc.perform(get("/test/task5/success")
                        .header(CorrelationIdConstants.HEADER_NAME, "second-request"))
                .andExpect(jsonPath("$.correlationId").value("second-request"));
    }

    @Test
    void doesNotLogOrReturnInvalidCorrelationValue() throws Exception {
        String suppliedValue = "task5-sensitive-invalid-correlation-marker,second";
        ListAppender<ILoggingEvent> appender = captureLogs(CorrelationIdFilter.class);

        String response = mockMvc.perform(get("/test/task5/success")
                        .header(CorrelationIdConstants.HEADER_NAME, suppliedValue))
                .andExpect(status().isBadRequest())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(response).doesNotContain(suppliedValue);
        assertThat(logText(appender)).doesNotContain(suppliedValue);
        assertThat(logArguments(appender)).doesNotContain(suppliedValue);
        detachLogs(CorrelationIdFilter.class, appender);
    }

    @Test
    void mapsMalformedJsonSyntaxToMalformedJsonProblem() throws Exception {
        String correlationId = "malformed-json-request";
        String response = mockMvc.perform(post("/test/task5/validated")
                        .header(CorrelationIdConstants.HEADER_NAME, correlationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"review\",\"quantity\":2"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string(CorrelationIdConstants.HEADER_NAME, correlationId))
                .andExpect(jsonPath("$.type").value(
                        "urn:connected-appliance-platform:problem:malformed-json"))
                .andExpect(jsonPath("$.title").value("Malformed JSON"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value(
                        "The request body contains malformed JSON."))
                .andExpect(jsonPath("$.instance").value("/test/task5/validated"))
                .andExpect(jsonPath("$.code").value("MALFORMED_JSON"))
                .andExpect(jsonPath("$.correlationId").value(correlationId))
                .andExpect(jsonPath("$.timestamp", matchesPattern(TIMESTAMP_PATTERN)))
                .andExpect(jsonPath("$.errors").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(response).doesNotContain("JsonParseException", "Unexpected end-of-input");
    }

    @Test
    void mapsUnknownJsonPropertyToValidationError() throws Exception {
        String rejectedValue = "task5-rejected-field-value";

        String response = mockMvc.perform(post("/test/task5/validated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"review","quantity":2,"unexpected":"%s"}
                                """.formatted(rejectedValue)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("unexpected"))
                .andExpect(jsonPath("$.errors[0].code").value("UNKNOWN_FIELD"))
                .andExpect(jsonPath("$.errors[0].message").value("is not recognized"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(response).doesNotContain(rejectedValue, "UnrecognizedPropertyException");
    }

    @Test
    void mapsBeanValidationRangeFailureToOutOfRange() throws Exception {
        String response = mockMvc.perform(post("/test/task5/validated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"review\",\"quantity\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("quantity"))
                .andExpect(jsonPath("$.errors[0].code").value("OUT_OF_RANGE"))
                .andExpect(jsonPath("$.errors[0].message").value(
                        "must be within the allowed range"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(response).doesNotContain("\"rejectedValue\"", "\"quantity\":0");
    }

    @Test
    void mapsBlankRequiredValueToRequired() throws Exception {
        mockMvc.perform(post("/test/task5/validated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"quantity\":2}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].code").value("REQUIRED"));
    }

    @Test
    void mapsMissingRequiredFieldToRequired() throws Exception {
        mockMvc.perform(post("/test/task5/validated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":2}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].code").value("REQUIRED"));
    }

    @Test
    void mapsJsonTypeMismatchToInvalidFormat() throws Exception {
        mockMvc.perform(post("/test/task5/validated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"review\",\"quantity\":\"many\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("quantity"))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_FORMAT"));
    }

    @Test
    void mapsMissingQueryParameterToRequired() throws Exception {
        mockMvc.perform(get("/test/task5/parameter"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("quantity"))
                .andExpect(jsonPath("$.errors[0].code").value("REQUIRED"));
    }

    @Test
    void mapsQueryParameterTypeMismatchToInvalidFormat() throws Exception {
        mockMvc.perform(get("/test/task5/parameter").queryParam("quantity", "many"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("quantity"))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_FORMAT"));
    }

    @Test
    void mapsQueryParameterConstraintViolationToOutOfRange() throws Exception {
        mockMvc.perform(get("/test/task5/parameter").queryParam("quantity", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("quantity"))
                .andExpect(jsonPath("$.errors[0].code").value("OUT_OF_RANGE"));
    }

    @Test
    void acceptsValidJsonWithoutProblemDetail() throws Exception {
        mockMvc.perform(post("/test/task5/validated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"review\",\"quantity\":2}"))
                .andExpect(status().isNoContent())
                .andExpect(header().string(
                        CorrelationIdConstants.HEADER_NAME,
                        matchesPattern(UUID_PATTERN)))
                .andExpect(content().string(""));
    }

    @Test
    void sanitizesUnexpectedFailureResponseAndLogs() throws Exception {
        ListAppender<ILoggingEvent> appender = captureLogs(ApiExceptionHandler.class);

        var result = mockMvc.perform(get("/test/task5/failure"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string(
                        CorrelationIdConstants.HEADER_NAME,
                        matchesPattern(UUID_PATTERN)))
                .andExpect(jsonPath("$.type").value(
                        "urn:connected-appliance-platform:problem:internal-error"))
                .andExpect(jsonPath("$.title").value("Internal server error"))
                .andExpect(jsonPath("$.detail").value(
                        "The request could not be completed due to an internal error."))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.correlationId", matchesPattern(UUID_PATTERN)))
                .andReturn();

        String response = result.getResponse().getContentAsString();
        String headerCorrelationId = result.getResponse()
                .getHeader(CorrelationIdConstants.HEADER_NAME);
        String bodyCorrelationId = objectMapper.readTree(response)
                .path("correlationId")
                .asText();

        assertThat(bodyCorrelationId).isEqualTo(headerCorrelationId);
        assertThat(response).doesNotContain(
                Task5FixtureController.SENSITIVE_FAILURE_MARKER,
                "IllegalStateException",
                "internal_table",
                "password",
                "stackTrace");
        assertThat(logText(appender))
                .doesNotContain(Task5FixtureController.SENSITIVE_FAILURE_MARKER);
        assertThat(logArguments(appender))
                .doesNotContain(Task5FixtureController.SENSITIVE_FAILURE_MARKER);
        detachLogs(ApiExceptionHandler.class, appender);
    }

    @Test
    void mapsUnmappedResourceToSanitizedNotFoundProblem() throws Exception {
        var result = mockMvc.perform(get("/task5-missing-resource")
                        .queryParam("internalResourceDescription", "task5-sensitive-query"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string(
                        CorrelationIdConstants.HEADER_NAME,
                        matchesPattern(UUID_PATTERN)))
                .andExpect(jsonPath("$.type").value(
                        "urn:connected-appliance-platform:problem:not-found"))
                .andExpect(jsonPath("$.title").value("Resource not found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value(
                        "The requested resource was not found."))
                .andExpect(jsonPath("$.instance").value("/task5-missing-resource"))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.correlationId", matchesPattern(UUID_PATTERN)))
                .andExpect(jsonPath("$.timestamp", matchesPattern(TIMESTAMP_PATTERN)))
                .andReturn();

        String response = result.getResponse().getContentAsString();
        String headerCorrelationId = result.getResponse()
                .getHeader(CorrelationIdConstants.HEADER_NAME);
        String bodyCorrelationId = objectMapper.readTree(response)
                .path("correlationId")
                .asText();

        assertThat(bodyCorrelationId).isEqualTo(headerCorrelationId);
        assertThat(response).doesNotContain(
                "task5-sensitive-query",
                "No static resource",
                "NoResourceFoundException",
                "ResourceHttpRequestHandler");
    }

    private static Stream<String> invalidCorrelationIds() {
        return Stream.of(
                "contains space",
                ".starts-with-period",
                "comma,separated",
                "prohibited@character",
                " surrounding-whitespace ");
    }

    private static ListAppender<ILoggingEvent> captureLogs(Class<?> type) {
        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(type);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private static void detachLogs(Class<?> type, ListAppender<ILoggingEvent> appender) {
        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(type);
        logger.detachAppender(appender);
        appender.stop();
    }

    private static String logText(ListAppender<ILoggingEvent> appender) {
        return appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .reduce("", (first, second) -> first + "\n" + second);
    }

    private static String logArguments(ListAppender<ILoggingEvent> appender) {
        return appender.list.stream()
                .flatMap(event -> Arrays.stream(
                        event.getArgumentArray() == null
                                ? new Object[0]
                                : event.getArgumentArray()))
                .map(String::valueOf)
                .reduce("", (first, second) -> first + "\n" + second);
    }
}
