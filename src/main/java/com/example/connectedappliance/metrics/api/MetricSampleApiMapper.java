package com.example.connectedappliance.metrics.api;

import com.example.connectedappliance.metrics.domain.MetricSample;
import org.springframework.stereotype.Component;

/** Explicit mapping from normalized Metrics domain samples to public DTOs. */
@Component
public final class MetricSampleApiMapper {

    public MetricSampleResponse toResponse(MetricSample sample) {
        return new MetricSampleResponse(
                sample.id(),
                sample.applianceId(),
                sample.collectionAttemptId(),
                sample.metricName(),
                sample.unit(),
                sample.value(),
                sample.observedAt(),
                sample.ingestedAt());
    }
}
