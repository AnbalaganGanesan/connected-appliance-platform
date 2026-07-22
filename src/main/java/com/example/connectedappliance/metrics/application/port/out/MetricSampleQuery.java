package com.example.connectedappliance.metrics.application.port.out;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Persistence-neutral normalized-history query with {@code [from,to)} boundaries. */
public record MetricSampleQuery(
        UUID applianceId,
        Instant fromInclusive,
        Instant toExclusive,
        HistoryPageRequest pageRequest) {

    public MetricSampleQuery {
        Objects.requireNonNull(applianceId, "applianceId must not be null");
        Objects.requireNonNull(fromInclusive, "fromInclusive must not be null");
        Objects.requireNonNull(toExclusive, "toExclusive must not be null");
        Objects.requireNonNull(pageRequest, "pageRequest must not be null");
        if (!fromInclusive.isBefore(toExclusive)) {
            throw new IllegalArgumentException("fromInclusive must be before toExclusive");
        }
    }
}
