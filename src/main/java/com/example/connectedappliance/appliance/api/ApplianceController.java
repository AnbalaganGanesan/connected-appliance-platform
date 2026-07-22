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
import org.springframework.web.bind.annotation.RestController;

import com.example.connectedappliance.appliance.application.ApplianceRegistrationService;
import com.example.connectedappliance.appliance.application.ApplianceRetrievalService;

/** Public registration and single-resource retrieval endpoints for the first Appliance slice. */
@RestController
@RequestMapping(path = "/api/v1/appliances", produces = MediaType.APPLICATION_JSON_VALUE)
public final class ApplianceController {

    private final ApplianceRegistrationService registrationService;
    private final ApplianceRetrievalService retrievalService;
    private final ApplianceApiMapper mapper;

    public ApplianceController(
            ApplianceRegistrationService registrationService,
            ApplianceRetrievalService retrievalService,
            ApplianceApiMapper mapper) {
        this.registrationService = registrationService;
        this.retrievalService = retrievalService;
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
}
