package com.example.connectedappliance.appliance.api;

import java.net.URI;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.MultiValueMap;

import com.example.connectedappliance.appliance.application.ApplianceListingService;
import com.example.connectedappliance.appliance.application.ApplianceRegistrationService;
import com.example.connectedappliance.appliance.application.ApplianceRetrievalService;
import com.example.connectedappliance.shared.api.PageResponse;

/** Public registration, retrieval, and fixed-order listing endpoints for Appliance. */
@RestController
@RequestMapping(path = "/api/v1/appliances", produces = MediaType.APPLICATION_JSON_VALUE)
public final class ApplianceController {

    private final ApplianceRegistrationService registrationService;
    private final ApplianceRetrievalService retrievalService;
    private final ApplianceListingService listingService;
    private final ApplianceApiMapper mapper;

    public ApplianceController(
            ApplianceRegistrationService registrationService,
            ApplianceRetrievalService retrievalService,
            ApplianceListingService listingService,
            ApplianceApiMapper mapper) {
        this.registrationService = registrationService;
        this.retrievalService = retrievalService;
        this.listingService = listingService;
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
}
