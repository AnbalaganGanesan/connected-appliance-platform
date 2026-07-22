package com.example.connectedappliance.metrics.api;

import java.util.UUID;

import com.example.connectedappliance.metrics.application.history.MetricsHistoryQueryService;
import com.example.connectedappliance.metrics.application.port.out.CollectionAttemptPage;
import com.example.connectedappliance.metrics.application.port.out.MetricSamplePage;
import com.example.connectedappliance.shared.api.PageResponse;
import com.example.connectedappliance.shared.validation.UtcInstantQueryParser;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public read-only endpoints for persisted collection and normalized metric history. */
@RestController
@Lazy
public final class MetricsHistoryController {

    private final MetricsHistoryQueryService queryService;
    private final CollectionAttemptApiMapper attemptMapper;
    private final MetricSampleApiMapper sampleMapper;
    private final UtcInstantQueryParser timestampParser;

    public MetricsHistoryController(
            MetricsHistoryQueryService queryService,
            CollectionAttemptApiMapper attemptMapper,
            MetricSampleApiMapper sampleMapper,
            UtcInstantQueryParser timestampParser) {
        this.queryService = queryService;
        this.attemptMapper = attemptMapper;
        this.sampleMapper = sampleMapper;
        this.timestampParser = timestampParser;
    }

    @GetMapping(
            path = "/api/v1/appliances/{applianceId}/collection-attempts",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public PageResponse<CollectionAttemptResponse> collectionAttempts(
            @PathVariable UUID applianceId,
            @RequestParam MultiValueMap<String, String> queryParameters) {
        MetricsHistoryQueryParameters.AttemptParameters query =
                MetricsHistoryQueryParameters.attempts(queryParameters);
        return attempts(queryService.getCollectionAttempts(
                applianceId,
                query.trigger(),
                query.outcome(),
                query.page(),
                query.size()));
    }

    @GetMapping(
            path = "/api/v1/appliances/{applianceId}/metrics",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public PageResponse<MetricSampleResponse> metricSamples(
            @PathVariable UUID applianceId,
            @RequestParam MultiValueMap<String, String> queryParameters) {
        MetricsHistoryQueryParameters.MetricParameters query =
                MetricsHistoryQueryParameters.metrics(queryParameters, timestampParser);
        return samples(queryService.getMetricSamples(
                applianceId,
                query.from(),
                query.to(),
                query.page(),
                query.size()));
    }

    private PageResponse<CollectionAttemptResponse> attempts(CollectionAttemptPage page) {
        return new PageResponse<>(
                page.items().stream().map(attemptMapper::toResponse).toList(),
                page.page(),
                page.size(),
                page.totalElements(),
                page.totalPages());
    }

    private PageResponse<MetricSampleResponse> samples(MetricSamplePage page) {
        return new PageResponse<>(
                page.items().stream().map(sampleMapper::toResponse).toList(),
                page.page(),
                page.size(),
                page.totalElements(),
                page.totalPages());
    }
}
