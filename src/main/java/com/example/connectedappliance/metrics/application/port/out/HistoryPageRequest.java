package com.example.connectedappliance.metrics.application.port.out;

/** Zero-based persistence pagination without a public-API size cap. */
public record HistoryPageRequest(int page, int size) {

    public HistoryPageRequest {
        if (page < 0) {
            throw new IllegalArgumentException("page must not be negative");
        }
        if (size <= 0) {
            throw new IllegalArgumentException("size must be positive");
        }
    }
}
