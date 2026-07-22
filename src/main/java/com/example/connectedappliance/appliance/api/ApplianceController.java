package com.example.connectedappliance.appliance.api;

import java.net.URI;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.MultiValueMap;

import com.example.connectedappliance.appliance.application.ApplianceListingService;
import com.example.connectedappliance.appliance.application.ApplianceCollectionConfigurationService;
import com.example.connectedappliance.appliance.application.ApplianceMetadataService;
import com.example.connectedappliance.appliance.application.ApplianceRegistrationService;
import com.example.connectedappliance.appliance.application.ApplianceRetrievalService;
import com.example.connectedappliance.shared.api.PageResponse;
import com.example.connectedappliance.shared.error.RequestValidationException;

/** Public registration, retrieval, listing, and management endpoints for Appliance. */
@RestController
@RequestMapping(path = "/api/v1/appliances", produces = MediaType.APPLICATION_JSON_VALUE)
public final class ApplianceController {

    private final ApplianceRegistrationService registrationService;
    private final ApplianceRetrievalService retrievalService;
    private final ApplianceListingService listingService;
    private final ApplianceMetadataService metadataService;
    private final ApplianceCollectionConfigurationService collectionConfigurationService;
    private final ApplianceApiMapper mapper;

    public ApplianceController(
            ApplianceRegistrationService registrationService,
            ApplianceRetrievalService retrievalService,
            ApplianceListingService listingService,
            ApplianceMetadataService metadataService,
            ApplianceCollectionConfigurationService collectionConfigurationService,
            ApplianceApiMapper mapper) {
        this.registrationService = registrationService;
        this.retrievalService = retrievalService;
        this.listingService = listingService;
        this.metadataService = metadataService;
        this.collectionConfigurationService = collectionConfigurationService;
        this.mapper = mapper;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApplianceResponse> register(
            @Valid @RequestBody RegisterApplianceRequest request) {
        ApplianceResponse response = mapper.toResponse(
                registrationService.register(mapper.toCommand(request)));
        URI location = URI.create("/api/v1/appliances/" + response.id());
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{applianceId}")
    public ApplianceResponse get(@PathVariable UUID applianceId) {
        return mapper.toResponse(retrievalService.get(applianceId));
    }

    @GetMapping
    public PageResponse<ApplianceResponse> list(
            @RequestParam MultiValueMap<String, String> queryParameters) {
        ApplianceListQueryParameters query = ApplianceListQueryParameters.parse(queryParameters);
        return mapper.toPageResponse(listingService.list(
                query.page(), query.size(), query.collectionState()));
    }

    @PutMapping(path = "/{applianceId}/metadata", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApplianceResponse replaceMetadata(
            @PathVariable UUID applianceId,
            @RequestParam MultiValueMap<String, String> queryParameters,
            @Valid @RequestBody UpdateApplianceMetadataRequest request) {
        rejectQueryParameters(queryParameters);
        return mapper.toResponse(metadataService.replace(mapper.toCommand(applianceId, request)));
    }

    @PutMapping(
            path = "/{applianceId}/collection-interval",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApplianceResponse replaceCollectionInterval(
            @PathVariable UUID applianceId,
            @RequestParam MultiValueMap<String, String> queryParameters,
            @Valid @RequestBody UpdateCollectionIntervalRequest request) {
        rejectQueryParameters(queryParameters);
        return mapper.toResponse(collectionConfigurationService.updateCollectionInterval(
                mapper.toCommand(applianceId, request)));
    }

    @PutMapping(
            path = "/{applianceId}/collection-state",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApplianceResponse replaceCollectionState(
            @PathVariable UUID applianceId,
            @RequestParam MultiValueMap<String, String> queryParameters,
            @Valid @RequestBody UpdateCollectionStateRequest request) {
        rejectQueryParameters(queryParameters);
        return mapper.toResponse(collectionConfigurationService.updateCollectionState(
                mapper.toCommand(applianceId, request)));
    }

    private static void rejectQueryParameters(
            MultiValueMap<String, String> queryParameters) {
        queryParameters.keySet().stream().sorted().findFirst().ifPresent(parameter -> {
            throw new RequestValidationException(parameter, "UNKNOWN_FIELD");
        });
    }
}
