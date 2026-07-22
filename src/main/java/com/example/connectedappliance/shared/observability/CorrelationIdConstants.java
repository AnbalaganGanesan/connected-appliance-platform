package com.example.connectedappliance.shared.observability;

public final class CorrelationIdConstants {

    public static final String HEADER_NAME = "X-Correlation-ID";
    public static final String MDC_KEY = "correlationId";
    public static final String REQUEST_ATTRIBUTE =
            "com.example.connectedappliance.correlationId";

    private CorrelationIdConstants() {
    }
}
