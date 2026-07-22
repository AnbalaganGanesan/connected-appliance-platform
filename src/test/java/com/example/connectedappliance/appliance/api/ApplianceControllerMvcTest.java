package com.example.connectedappliance.appliance.api;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.connectedappliance.appliance.api.error.ApplianceApiExceptionHandler;
import com.example.connectedappliance.appliance.application.ApplianceRegistrationService;
import com.example.connectedappliance.appliance.application.ApplianceRetrievalService;
import com.example.connectedappliance.appliance.application.exception.ApplianceNotFoundException;
import com.example.connectedappliance.appliance.application.exception.DuplicateApplianceException;
import com.example.connectedappliance.appliance.application.exception.UnsupportedVendorException;
import com.example.connectedappliance.appliance.domain.Appliance;
import com.example.connectedappliance.appliance.domain.CollectionState;
import com.example.connectedappliance.appliance.domain.LastCollectionStatus;
import com.example.connectedappliance.bootstrap.UtcClockConfiguration;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ApplianceController.class)
@Import({
        UtcClockConfiguration.class,
        CorrelationIdService.class,
        CorrelationIdFilter.class,
        ValidationCodeMapper.class,
        ValidationErrorMapper.class,
        ProblemDetailFactory.class,
        ProblemResponseWriter.class,
        ApiExceptionHandler.class,
        ApplianceApiMapper.class,
        ApplianceApiExceptionHandler.class
})
class ApplianceControllerMvcTest {

    private static final UUID APPLIANCE_ID =
            UUID.fromString("2f1b71b7-71a1-4b6c-9d68-54ed3bc24618");
    private static final String CORRELATION_ID = "task10-review";
    private static final String UUID_PATTERN =
            "[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ApplianceRegistrationService registrationService;

    @MockitoBean
    private ApplianceRetrievalService retrievalService;

    @Test
    void registersApplianceWithCreatedLocationDtoBodyAndCorrelationId() throws Exception {
        when(registrationService.register(any())).thenReturn(appliance());

        mockMvc.perform(post("/api/v1/appliances")
                        .header(CorrelationIdConstants.HEADER_NAME, CORRELATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(header().string(
                        "Location", "/api/v1/appliances/" + APPLIANCE_ID))
                .andExpect(header().string(CorrelationIdConstants.HEADER_NAME, CORRELATION_ID))
                .andExpect(jsonPath("$.id").value(APPLIANCE_ID.toString()))
                .andExpect(jsonPath("$.displayName").value("Kitchen appliance"))
                .andExpect(jsonPath("$.description").value("Reviewer appliance"))
                .andExpect(jsonPath("$.vendorKey").value("mock-alpha"))
                .andExpect(jsonPath("$.externalReference").value("MixedCase-Ref"))
                .andExpect(jsonPath("$.collectionState").value("ACTIVE"))
                .andExpect(jsonPath("$.collectionIntervalSeconds").value(30))
                .andExpect(jsonPath("$.nextCollectionDueAt").value("2026-07-21T10:00:30Z"))
                .andExpect(jsonPath("$.consecutiveFailureCount").value(0))
                .andExpect(jsonPath("$.lastCollectionStatus").value("NEVER_ATTEMPTED"))
                .andExpect(jsonPath("$.createdAt").value("2026-07-21T10:00:00Z"))
                .andExpect(jsonPath("$.updatedAt").value("2026-07-21T10:00:00Z"))
                .andExpect(jsonPath("$.version").doesNotExist());
    }

    @Test
    void retrievesApplianceAsDtoWithCorrelationId() throws Exception {
        when(retrievalService.get(APPLIANCE_ID)).thenReturn(appliance());

        mockMvc.perform(get("/api/v1/appliances/{applianceId}", APPLIANCE_ID)
                        .header(CorrelationIdConstants.HEADER_NAME, CORRELATION_ID))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(header().string(CorrelationIdConstants.HEADER_NAME, CORRELATION_ID))
                .andExpect(jsonPath("$.id").value(APPLIANCE_ID.toString()))
                .andExpect(jsonPath("$.collectionState").value("ACTIVE"))
                .andExpect(jsonPath("$.version").doesNotExist());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRegistrationRequests")
    void mapsInvalidRegistrationFieldsToValidationProblem(
            String scenario, String requestBody, String expectedField, String expectedCode)
            throws Exception {
        String response = mockMvc.perform(post("/api/v1/appliances")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string(
                        CorrelationIdConstants.HEADER_NAME,
                        matchesPattern(UUID_PATTERN)))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[?(@.field == '%s' && @.code == '%s')]"
                        .formatted(expectedField, expectedCode)).exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(response).doesNotContain("rejectedValue", "ConstraintViolationException");
    }

    @Test
    void rejectsUnknownJsonFieldAsValidationErrorWithoutEchoingValue() throws Exception {
        String rejected = "task10-sensitive-unknown-value";
        String request = validRequest().replace(
                "\"collectionIntervalSeconds\":30",
                "\"collectionIntervalSeconds\":30,\"vendorSecret\":\"" + rejected + "\"");

        String response = mockMvc.perform(post("/api/v1/appliances")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("vendorSecret"))
                .andExpect(jsonPath("$.errors[0].code").value("UNKNOWN_FIELD"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(response).doesNotContain(rejected);
    }

    @Test
    void keepsMalformedJsonDistinctFromValidationErrors() throws Exception {
        mockMvc.perform(post("/api/v1/appliances")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Appliance\""))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("MALFORMED_JSON"));
    }

    @Test
    void mapsUnsupportedVendorToSanitizedUnprocessableEntityProblem() throws Exception {
        when(registrationService.register(any())).thenThrow(new UnsupportedVendorException());

        var result = mockMvc.perform(post("/api/v1/appliances")
                        .header(CorrelationIdConstants.HEADER_NAME, CORRELATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest().replace("mock-alpha", "unknown-vendor")))
                .andExpect(status().isUnprocessableEntity())
                .andReturn();

        assertProblem(
                result.getResponse().getContentAsString(),
                result.getResponse().getContentType(),
                result.getResponse().getHeader(CorrelationIdConstants.HEADER_NAME),
                422,
                "UNSUPPORTED_VENDOR",
                "unsupported-vendor",
                "Unsupported vendor",
                "The supplied vendor key is not supported.",
                "/api/v1/appliances");
        assertThat(result.getResponse().getContentAsString()).doesNotContain("unknown-vendor");
    }

    @Test
    void mapsDuplicateRegistrationToSanitizedConflictProblem() throws Exception {
        when(registrationService.register(any())).thenThrow(new DuplicateApplianceException());

        var result = mockMvc.perform(post("/api/v1/appliances")
                        .header(CorrelationIdConstants.HEADER_NAME, CORRELATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isConflict())
                .andReturn();

        assertProblem(
                result.getResponse().getContentAsString(),
                result.getResponse().getContentType(),
                result.getResponse().getHeader(CorrelationIdConstants.HEADER_NAME),
                409,
                "DUPLICATE_APPLIANCE",
                "duplicate-appliance",
                "Duplicate appliance",
                "An appliance with the supplied vendor key and external reference already exists.",
                "/api/v1/appliances");
        assertThat(result.getResponse().getContentAsString())
                .doesNotContain("MixedCase-Ref", "uk_appliance", "SQLException");
    }

    @Test
    void mapsMissingApplianceToSanitizedFeatureNotFoundProblem() throws Exception {
        when(retrievalService.get(APPLIANCE_ID)).thenThrow(new ApplianceNotFoundException());

        var result = mockMvc.perform(get("/api/v1/appliances/{applianceId}", APPLIANCE_ID)
                        .header(CorrelationIdConstants.HEADER_NAME, CORRELATION_ID))
                .andExpect(status().isNotFound())
                .andReturn();

        assertProblem(
                result.getResponse().getContentAsString(),
                result.getResponse().getContentType(),
                result.getResponse().getHeader(CorrelationIdConstants.HEADER_NAME),
                404,
                "APPLIANCE_NOT_FOUND",
                "appliance-not-found",
                "Appliance not found",
                "No appliance exists with the supplied identifier.",
                "/api/v1/appliances/" + APPLIANCE_ID);
    }

    @Test
    void mapsMalformedUuidToValidationError() throws Exception {
        mockMvc.perform(get("/api/v1/appliances/not-a-uuid")
                        .header(CorrelationIdConstants.HEADER_NAME, CORRELATION_ID))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("applianceId"))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_FORMAT"))
                .andExpect(jsonPath("$.correlationId").value(CORRELATION_ID));
    }

    @Test
    void rejectsUnsupportedRequestMediaType() throws Exception {
        mockMvc.perform(post("/api/v1/appliances")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(validRequest()))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void rejectsUnsupportedResponseMediaType() throws Exception {
        mockMvc.perform(get("/api/v1/appliances/{applianceId}", APPLIANCE_ID)
                        .accept(MediaType.APPLICATION_XML))
                .andExpect(status().isNotAcceptable());
    }

    private void assertProblem(
            String response,
            String contentType,
            String headerCorrelationId,
            int status,
            String code,
            String typeSuffix,
            String title,
            String detail,
            String instance) throws Exception {
        var json = objectMapper.readTree(response);
        assertThat(contentType).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(headerCorrelationId).isEqualTo(CORRELATION_ID);
        assertThat(json.path("status").asInt()).isEqualTo(status);
        assertThat(json.path("code").asText()).isEqualTo(code);
        assertThat(json.path("type").asText()).isEqualTo(
                "urn:connected-appliance-platform:problem:" + typeSuffix);
        assertThat(json.path("title").asText()).isEqualTo(title);
        assertThat(json.path("detail").asText()).isEqualTo(detail);
        assertThat(json.path("instance").asText()).isEqualTo(instance);
        assertThat(json.path("correlationId").asText()).isEqualTo(headerCorrelationId);
        assertThat(json.path("timestamp").asText()).endsWith("Z");
        assertThat(response).doesNotContain(
                "stackTrace", "SQLException", "ConstraintViolationException", "Exception.class");
    }

    private String validRequest() {
        return """
                {
                  "displayName":"  Kitchen appliance  ",
                  "description":"  Reviewer appliance  ",
                  "vendorKey":"mock-alpha",
                  "externalReference":"MixedCase-Ref",
                  "collectionIntervalSeconds":30
                }
                """;
    }

    private Appliance appliance() {
        Instant createdAt = Instant.parse("2026-07-21T10:00:00Z");
        return new Appliance(
                APPLIANCE_ID,
                "Kitchen appliance",
                "Reviewer appliance",
                "mock-alpha",
                "MixedCase-Ref",
                CollectionState.ACTIVE,
                30,
                createdAt.plusSeconds(30),
                0,
                LastCollectionStatus.NEVER_ATTEMPTED,
                9,
                createdAt,
                createdAt);
    }

    private static Stream<Arguments> invalidRegistrationRequests() {
        return Stream.of(
                Arguments.of(
                        "missing display name",
                        "{\"vendorKey\":\"mock-alpha\",\"externalReference\":\"ref\",\"collectionIntervalSeconds\":30}",
                        "displayName",
                        "REQUIRED"),
                Arguments.of(
                        "blank display name",
                        "{\"displayName\":\"   \",\"vendorKey\":\"mock-alpha\",\"externalReference\":\"ref\",\"collectionIntervalSeconds\":30}",
                        "displayName",
                        "REQUIRED"),
                Arguments.of(
                        "display name over trimmed limit",
                        requestWith("displayName", "x".repeat(101)),
                        "displayName",
                        "INVALID_LENGTH"),
                Arguments.of(
                        "description over trimmed limit",
                        requestWith("description", "x".repeat(501)),
                        "description",
                        "INVALID_LENGTH"),
                Arguments.of(
                        "missing vendor key",
                        "{\"displayName\":\"Appliance\",\"externalReference\":\"ref\",\"collectionIntervalSeconds\":30}",
                        "vendorKey",
                        "REQUIRED"),
                Arguments.of(
                        "invalid vendor key format",
                        requestWith("vendorKey", "MOCK-ALPHA"),
                        "vendorKey",
                        "INVALID_FORMAT"),
                Arguments.of(
                        "missing external reference",
                        "{\"displayName\":\"Appliance\",\"vendorKey\":\"mock-alpha\",\"collectionIntervalSeconds\":30}",
                        "externalReference",
                        "REQUIRED"),
                Arguments.of(
                        "blank external reference",
                        requestWith("externalReference", "   "),
                        "externalReference",
                        "REQUIRED"),
                Arguments.of(
                        "external reference over limit",
                        requestWith("externalReference", "x".repeat(129)),
                        "externalReference",
                        "INVALID_LENGTH"),
                Arguments.of(
                        "missing interval",
                        "{\"displayName\":\"Appliance\",\"vendorKey\":\"mock-alpha\",\"externalReference\":\"ref\"}",
                        "collectionIntervalSeconds",
                        "REQUIRED"),
                Arguments.of(
                        "interval below minimum",
                        requestWithInterval(4),
                        "collectionIntervalSeconds",
                        "OUT_OF_RANGE"),
                Arguments.of(
                        "interval above maximum",
                        requestWithInterval(86_401),
                        "collectionIntervalSeconds",
                        "OUT_OF_RANGE"));
    }

    private static String requestWith(String field, String value) {
        String displayName = field.equals("displayName") ? value : "Appliance";
        String description = field.equals("description") ? value : "Description";
        String vendorKey = field.equals("vendorKey") ? value : "mock-alpha";
        String externalReference = field.equals("externalReference") ? value : "ref";
        return """
                {"displayName":"%s","description":"%s","vendorKey":"%s","externalReference":"%s","collectionIntervalSeconds":30}
                """.formatted(displayName, description, vendorKey, externalReference);
    }

    private static String requestWithInterval(int interval) {
        return """
                {"displayName":"Appliance","vendorKey":"mock-alpha","externalReference":"ref","collectionIntervalSeconds":%d}
                """.formatted(interval);
    }
}
