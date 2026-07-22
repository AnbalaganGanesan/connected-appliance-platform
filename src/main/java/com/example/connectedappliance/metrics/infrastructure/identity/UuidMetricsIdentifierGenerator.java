package com.example.connectedappliance.metrics.infrastructure.identity;

import java.util.UUID;

import com.example.connectedappliance.metrics.application.port.out.MetricsIdentifierGenerator;
import org.springframework.stereotype.Component;

/** Production UUID generator; databases never generate Metrics identifiers. */
@Component
public final class UuidMetricsIdentifierGenerator implements MetricsIdentifierGenerator {

    @Override
    public UUID nextIdentifier() {
        return UUID.randomUUID();
    }
}
