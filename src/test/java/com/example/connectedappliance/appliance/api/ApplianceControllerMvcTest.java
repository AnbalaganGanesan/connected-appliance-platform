package com.example.connectedappliance.appliance.api;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import com.example.connectedappliance.appliance.api.error.ApplianceApiExceptionHandler;
import com.example.connectedappliance.appliance.application.ApplianceListingService;
import com.example.connectedappliance.appliance.application.ApplianceMetadataService;
import com.example.connectedappliance.appliance.application.ApplianceRegistrationService;
import com.example.connectedappliance.appliance.application.ApplianceRetrievalService;
import com.example.connectedappliance.appliance.application.ReplaceApplianceMetadataCommand;
import com.example.connectedappliance.appliance.application.exception.ApplianceNotFoundException;
import com.example.connectedappliance.appliance.application.exception.DuplicateApplianceException;
import com.example.connectedappliance.appliance.application.exception.UnsupportedVendorException;
import com.example.connectedappliance.appliance.domain.Appliance;
import com.example.connectedappliance.appliance.domain.CollectionState;
import com.example.connectedappliance.appliance.domain.LastCollectionStatus;
import com.example.connectedappliance.appliance.application.port.out.AppliancePage;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

    @MockitoBean
    private ApplianceListingService listingService;

    @MockitoBean
    private ApplianceMetadataService metadataService;

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

    @Test
    void listsWithApprovedDefaultsExactResponseContractAndCorrelationId() throws Exception {
        Appliance first = appliance();
        Appliance second = appliance(
                UUID.fromString("2f1b71b7-71a1-4b6c-9d68-54ed3bc24619"),
                "Second appliance",
                CollectionState.PAUSED,
                Instant.parse("2026-07-21T10:01:00Z"));
        when(listingService.list(0, 20, Optional.empty()))
                .thenReturn(new AppliancePage(List.of(first, second), 0, 20, 2, 1));

        var result = mockMvc.perform(get("/api/v1/appliances")
                        .header(CorrelationIdConstants.HEADER_NAME, CORRELATION_ID))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(header().string(CorrelationIdConstants.HEADER_NAME, CORRELATION_ID))
                .andExpect(jsonPath("$.items[0].id").value(first.id().toString()))
                .andExpect(jsonPath("$.items[1].id").value(second.id().toString()))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.items[0].version").doesNotExist())
                .andExpect(jsonPath("$.content").doesNotExist())
                .andExpect(jsonPath("$.sort").doesNotExist())
                .andExpect(jsonPath("$.pageable").doesNotExist())
                .andReturn();

        var json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(fieldNames(json)).containsExactly(
                "items", "page", "size", "totalElements", "totalPages");
        assertThat(fieldNames(json.path("items").get(0))).containsExactly(
                "collectionIntervalSeconds",
                "collectionState",
                "consecutiveFailureCount",
                "createdAt",
                "description",
                "displayName",
                "externalReference",
                "id",
                "lastCollectionStatus",
                "nextCollectionDueAt",
                "updatedAt",
                "vendorKey");
        verify(listingService).list(0, 20, Optional.empty());
    }

    @Test
    void listsWithExplicitPageAndSizeAndPreservesMetadata() throws Exception {
        when(listingService.list(1, 10, Optional.empty()))
                .thenReturn(new AppliancePage(List.of(), 1, 10, 23, 3));

        mockMvc.perform(get("/api/v1/appliances")
                        .queryParam("page", "1")
                        .queryParam("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(23))
                .andExpect(jsonPath("$.totalPages").value(3));

        verify(listingService).list(1, 10, Optional.empty());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 100})
    void acceptsInclusivePublicSizeBoundaries(int size) throws Exception {
        when(listingService.list(0, size, Optional.empty()))
                .thenReturn(new AppliancePage(List.of(), 0, size, 0, 0));

        mockMvc.perform(get("/api/v1/appliances")
                        .queryParam("size", Integer.toString(size)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(size));

        verify(listingService).list(0, size, Optional.empty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACTIVE", "PAUSED"})
    void delegatesEachExactCollectionStateFilter(String rawState) throws Exception {
        CollectionState state = CollectionState.valueOf(rawState);
        when(listingService.list(0, 20, Optional.of(state)))
                .thenReturn(new AppliancePage(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/v1/appliances")
                        .queryParam("collectionState", rawState))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());

        verify(listingService).list(0, 20, Optional.of(state));
    }

    @Test
    void returnsApprovedEmptyPageForNoRows() throws Exception {
        when(listingService.list(0, 20, Optional.empty()))
                .thenReturn(new AppliancePage(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/v1/appliances"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    void returnsApprovedEmptyPageBeyondFinalPageWithoutResettingMetadata() throws Exception {
        when(listingService.list(5, 2, Optional.empty()))
                .thenReturn(new AppliancePage(List.of(), 5, 2, 3, 2));

        mockMvc.perform(get("/api/v1/appliances")
                        .queryParam("page", "5")
                        .queryParam("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.page").value(5))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidPaginationQueries")
    void rejectsInvalidPaginationAsSanitizedValidationProblem(
            String scenario, String parameter, String value, String expectedCode) throws Exception {
        var result = mockMvc.perform(get("/api/v1/appliances")
                        .header(CorrelationIdConstants.HEADER_NAME, CORRELATION_ID)
                        .queryParam(parameter, value))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.instance").value("/api/v1/appliances"))
                .andExpect(jsonPath("$.correlationId").value(CORRELATION_ID))
                .andExpect(jsonPath("$.errors[0].field").value(parameter))
                .andExpect(jsonPath("$.errors[0].code").value(expectedCode))
                .andReturn();

        var json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(fieldNames(json.path("errors").get(0)))
                .containsExactly("code", "field", "message");
        assertThat(result.getResponse().getContentAsString()).doesNotContain("rejectedValue");
        verifyNoInteractions(listingService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"active", "paused", "UNKNOWN", "", "ACTIVE,PAUSED"})
    void rejectsInvalidCollectionStateWithoutNormalizationOrValueEcho(String value)
            throws Exception {
        var result = mockMvc.perform(get("/api/v1/appliances")
                        .queryParam("collectionState", value))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("collectionState"))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_FORMAT"))
                .andReturn();

        if (!value.isEmpty()) {
            assertThat(result.getResponse().getContentAsString()).doesNotContain(value);
        }
        verifyNoInteractions(listingService);
    }

    @Test
    void rejectsRepeatedCollectionStateInsteadOfSelectingOneValue() throws Exception {
        String response = mockMvc.perform(get("/api/v1/appliances")
                        .queryParam("collectionState", "ACTIVE", "PAUSED"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("collectionState"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(response).doesNotContain("ACTIVE", "PAUSED");
        verifyNoInteractions(listingService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"sort", "order", "vendorKey", "unexpected"})
    void rejectsUnsupportedQueryParametersWithoutEchoingValues(String parameter)
            throws Exception {
        String rejectedValue = "task11-sensitive-query-value";

        String response = mockMvc.perform(get("/api/v1/appliances")
                        .queryParam(parameter, rejectedValue))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value(parameter))
                .andExpect(jsonPath("$.errors[0].code").value("UNKNOWN_FIELD"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(response).doesNotContain(rejectedValue);
        verifyNoInteractions(listingService);
    }

    @Test
    void replacesMetadataWithNormalizedCommandAndExistingResponseContract() throws Exception {
        when(metadataService.replace(any())).thenAnswer(invocation -> {
            ReplaceApplianceMetadataCommand command = invocation.getArgument(0);
            return applianceWithMetadata(command.displayName(), command.description());
        });

        mockMvc.perform(put("/api/v1/appliances/{applianceId}/metadata", APPLIANCE_ID)
                        .header(CorrelationIdConstants.HEADER_NAME, CORRELATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"  Kitchen replacement  ",
                                 "description":"  Ground floor  "}
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(header().string(CorrelationIdConstants.HEADER_NAME, CORRELATION_ID))
                .andExpect(jsonPath("$.displayName").value("Kitchen replacement"))
                .andExpect(jsonPath("$.description").value("Ground floor"))
                .andExpect(jsonPath("$.vendorKey").value("mock-alpha"))
                .andExpect(jsonPath("$.collectionState").value("ACTIVE"))
                .andExpect(jsonPath("$.version").doesNotExist());

        var captor = org.mockito.ArgumentCaptor.forClass(ReplaceApplianceMetadataCommand.class);
        verify(metadataService).replace(captor.capture());
        assertThat(captor.getValue()).isEqualTo(new ReplaceApplianceMetadataCommand(
                APPLIANCE_ID, "Kitchen replacement", "Ground floor"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("descriptionClearingRequests")
    void clearsDescriptionForExplicitNullAndOmittedField(String scenario, String requestBody)
            throws Exception {
        when(metadataService.replace(any())).thenAnswer(invocation -> {
            ReplaceApplianceMetadataCommand command = invocation.getArgument(0);
            return applianceWithMetadata(command.displayName(), command.description());
        });

        mockMvc.perform(put("/api/v1/appliances/{applianceId}/metadata", APPLIANCE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Kitchen"))
                .andExpect(jsonPath("$.description").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.version").doesNotExist());

        var captor = org.mockito.ArgumentCaptor.forClass(ReplaceApplianceMetadataCommand.class);
        verify(metadataService).replace(captor.capture());
        assertThat(captor.getValue().description()).isNull();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidMetadataRequests")
    void rejectsInvalidMetadataWithSanitizedValidationProblem(
            String scenario, String requestBody, String expectedField, String expectedCode)
            throws Exception {
        String response = mockMvc.perform(put(
                                "/api/v1/appliances/{applianceId}/metadata", APPLIANCE_ID)
                        .header(CorrelationIdConstants.HEADER_NAME, CORRELATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string(CorrelationIdConstants.HEADER_NAME, CORRELATION_ID))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.correlationId").value(CORRELATION_ID))
                .andExpect(jsonPath("$.errors[0].field").value(expectedField))
                .andExpect(jsonPath("$.errors[0].code").value(expectedCode))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(response).doesNotContain("rejectedValue", "ConstraintViolationException");
        verifyNoInteractions(metadataService);
    }

    @Test
    void rejectsUnknownMetadataFieldWithoutEchoingItsValue() throws Exception {
        String sensitiveValue = "task12-sensitive-unknown-value";

        String response = mockMvc.perform(put(
                                "/api/v1/appliances/{applianceId}/metadata", APPLIANCE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"Kitchen","vendorKey":"%s"}
                                """.formatted(sensitiveValue)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("vendorKey"))
                .andExpect(jsonPath("$.errors[0].code").value("UNKNOWN_FIELD"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(response).doesNotContain(sensitiveValue);
        verifyNoInteractions(metadataService);
    }

    @Test
    void keepsMalformedMetadataJsonDistinctFromValidationErrors() throws Exception {
        mockMvc.perform(put("/api/v1/appliances/{applianceId}/metadata", APPLIANCE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Kitchen\""))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("MALFORMED_JSON"));

        verifyNoInteractions(metadataService);
    }

    @Test
    void rejectsMalformedMetadataUuidBeforeCallingService() throws Exception {
        mockMvc.perform(put("/api/v1/appliances/not-a-uuid/metadata")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validMetadataRequest()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(metadataService);
    }

    @Test
    void mapsMissingMetadataTargetToExistingApplianceNotFoundProblem() throws Exception {
        doThrow(new ApplianceNotFoundException()).when(metadataService).replace(any());

        mockMvc.perform(put("/api/v1/appliances/{applianceId}/metadata", APPLIANCE_ID)
                        .header(CorrelationIdConstants.HEADER_NAME, CORRELATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validMetadataRequest()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("APPLIANCE_NOT_FOUND"))
                .andExpect(jsonPath("$.correlationId").value(CORRELATION_ID));
    }

    @ParameterizedTest
    @ValueSource(strings = {"force", "version", "unexpected"})
    void rejectsEveryMetadataQueryParameterWithoutEchoingValue(String parameter)
            throws Exception {
        String suppliedValue = "task12-sensitive-query-value";

        String response = mockMvc.perform(put(
                                "/api/v1/appliances/{applianceId}/metadata", APPLIANCE_ID)
                        .queryParam(parameter, suppliedValue)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validMetadataRequest()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value(parameter))
                .andExpect(jsonPath("$.errors[0].code").value("UNKNOWN_FIELD"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(response).doesNotContain(suppliedValue);
        verifyNoInteractions(metadataService);
    }

    @Test
    void rejectsUnsupportedMetadataRequestMediaType() throws Exception {
        mockMvc.perform(put("/api/v1/appliances/{applianceId}/metadata", APPLIANCE_ID)
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(validMetadataRequest()))
                .andExpect(status().isUnsupportedMediaType());

        verifyNoInteractions(metadataService);
    }

    @Test
    void rejectsUnacceptableMetadataResponseMediaType() throws Exception {
        mockMvc.perform(put("/api/v1/appliances/{applianceId}/metadata", APPLIANCE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_XML)
                        .content(validMetadataRequest()))
                .andExpect(status().isNotAcceptable());

        verifyNoInteractions(metadataService);
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

    private Appliance appliance(
            UUID id, String displayName, CollectionState state, Instant createdAt) {
        return new Appliance(
                id,
                displayName,
                null,
                "mock-alpha",
                "reference-" + id,
                state,
                30,
                state == CollectionState.ACTIVE ? createdAt.plusSeconds(30) : null,
                0,
                LastCollectionStatus.NEVER_ATTEMPTED,
                4,
                createdAt,
                createdAt);
    }

    private Appliance applianceWithMetadata(String displayName, String description) {
        Appliance current = appliance();
        return new Appliance(
                current.id(),
                displayName,
                description,
                current.vendorKey(),
                current.externalReference(),
                current.collectionState(),
                current.collectionIntervalSeconds(),
                current.nextCollectionDueAt(),
                current.consecutiveFailureCount(),
                current.lastCollectionStatus(),
                current.version(),
                current.createdAt(),
                current.updatedAt().plusSeconds(1));
    }

    private String validMetadataRequest() {
        return """
                {"displayName":"Kitchen","description":"Ground floor"}
                """;
    }

    private Set<String> fieldNames(com.fasterxml.jackson.databind.JsonNode node) {
        Set<String> names = new TreeSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
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

    private static Stream<Arguments> descriptionClearingRequests() {
        return Stream.of(
                Arguments.of(
                        "explicit null clears description",
                        "{\"displayName\":\"Kitchen\",\"description\":null}"),
                Arguments.of(
                        "omission clears description",
                        "{\"displayName\":\"Kitchen\"}"));
    }

    private static Stream<Arguments> invalidMetadataRequests() {
        return Stream.of(
                Arguments.of(
                        "missing display name",
                        "{\"description\":\"Description\"}",
                        "displayName",
                        "REQUIRED"),
                Arguments.of(
                        "null display name",
                        "{\"displayName\":null}",
                        "displayName",
                        "REQUIRED"),
                Arguments.of(
                        "blank display name",
                        "{\"displayName\":\"   \"}",
                        "displayName",
                        "REQUIRED"),
                Arguments.of(
                        "display name over trimmed limit",
                        "{\"displayName\":\"  %s  \"}".formatted("x".repeat(101)),
                        "displayName",
                        "INVALID_LENGTH"),
                Arguments.of(
                        "description over trimmed limit",
                        "{\"displayName\":\"Kitchen\",\"description\":\"  %s  \"}"
                                .formatted("x".repeat(501)),
                        "description",
                        "INVALID_LENGTH"));
    }

    private static Stream<Arguments> invalidPaginationQueries() {
        return Stream.of(
                Arguments.of("negative page", "page", "-1", "OUT_OF_RANGE"),
                Arguments.of("non-numeric page", "page", "abc", "INVALID_FORMAT"),
                Arguments.of("decimal page", "page", "1.5", "INVALID_FORMAT"),
                Arguments.of("blank page", "page", "", "INVALID_FORMAT"),
                Arguments.of("overflow page", "page", "2147483648", "INVALID_FORMAT"),
                Arguments.of("zero size", "size", "0", "OUT_OF_RANGE"),
                Arguments.of("negative size", "size", "-1", "OUT_OF_RANGE"),
                Arguments.of("oversized size", "size", "101", "OUT_OF_RANGE"),
                Arguments.of("non-numeric size", "size", "abc", "INVALID_FORMAT"),
                Arguments.of("decimal size", "size", "2.5", "INVALID_FORMAT"),
                Arguments.of("blank size", "size", "", "INVALID_FORMAT"),
                Arguments.of("overflow size", "size", "2147483648", "INVALID_FORMAT"));
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
