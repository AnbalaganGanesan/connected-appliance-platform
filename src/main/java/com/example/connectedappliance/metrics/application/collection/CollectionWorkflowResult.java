package com.example.connectedappliance.metrics.application.collection;

import java.util.Objects;

import com.example.connectedappliance.metrics.domain.CollectionAttempt;

/** Persistence-neutral outcome shared by future manual and scheduled collection callers. */
public sealed interface CollectionWorkflowResult
        permits CollectionWorkflowResult.Persisted,
                CollectionWorkflowResult.NotFound,
                CollectionWorkflowResult.Paused,
                CollectionWorkflowResult.Busy,
                CollectionWorkflowResult.Saturated {

    record Persisted(CollectionAttempt attempt) implements CollectionWorkflowResult {
        public Persisted {
            Objects.requireNonNull(attempt, "attempt must not be null");
        }
    }

    record NotFound() implements CollectionWorkflowResult {}

    record Paused() implements CollectionWorkflowResult {}

    record Busy() implements CollectionWorkflowResult {}

    record Saturated() implements CollectionWorkflowResult {}
}
