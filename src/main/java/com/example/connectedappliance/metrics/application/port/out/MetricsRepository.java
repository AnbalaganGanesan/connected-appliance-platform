package com.example.connectedappliance.metrics.application.port.out;

import com.example.connectedappliance.metrics.domain.CollectionAttempt;
import com.example.connectedappliance.metrics.domain.CompletedCollection;

/** Insertion-only persistence and fixed-order history queries owned by Metrics. */
public interface MetricsRepository {

    CollectionAttempt insert(CompletedCollection completedCollection);

    CollectionAttemptPage findAttempts(CollectionAttemptQuery query);

    MetricSamplePage findMetricSamples(MetricSampleQuery query);
}
