package com.example.connectedappliance.metrics.api;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.connectedappliance.metrics.application.collectnow.CollectNowService;
import com.example.connectedappliance.shared.error.RequestValidationException;
import com.example.connectedappliance.shared.error.ValidationItem;

/** Public synchronous manual metric-collection action. */
@RestController
@Lazy
public final class MetricCollectionController {

    private final CollectNowService collectNowService;
    private final CollectionAttemptApiMapper mapper;

    public MetricCollectionController(
            CollectNowService collectNowService, CollectionAttemptApiMapper mapper) {
        this.collectNowService = collectNowService;
        this.mapper = mapper;
    }

    @PostMapping(
            path = "/api/v1/appliances/{applianceId}/actions/collect-now",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public CollectionAttemptResponse collectNow(
            @PathVariable UUID applianceId,
            @RequestParam MultiValueMap<String, String> queryParameters,
            @RequestBody(required = false) JsonNode requestBody) {
        rejectQueryParameters(queryParameters);
        rejectRequestBody(requestBody);
        return mapper.toResponse(collectNowService.collectNow(applianceId));
    }

    private static void rejectQueryParameters(MultiValueMap<String, String> queryParameters) {
        queryParameters.keySet().stream().sorted().findFirst().ifPresent(parameter -> {
            throw new RequestValidationException(parameter, "UNKNOWN_FIELD");
        });
    }

    private static void rejectRequestBody(JsonNode requestBody) {
        if (requestBody != null) {
            throw new RequestValidationException(List.of(new ValidationItem(
                    "body", "UNKNOWN_FIELD", "request body is not supported")));
        }
    }
}
