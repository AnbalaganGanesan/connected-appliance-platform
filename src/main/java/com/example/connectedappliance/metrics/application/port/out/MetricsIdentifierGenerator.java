package com.example.connectedappliance.metrics.application.port.out;

import java.util.UUID;

/** Metrics-owned source of application-assigned attempt and sample identifiers. */
public interface MetricsIdentifierGenerator {

    UUID nextIdentifier();
}
