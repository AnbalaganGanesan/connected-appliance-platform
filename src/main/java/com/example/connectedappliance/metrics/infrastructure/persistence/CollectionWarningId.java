package com.example.connectedappliance.metrics.infrastructure.persistence;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
class CollectionWarningId implements Serializable {

    @Column(name = "collection_attempt_id", nullable = false, updatable = false)
    private UUID collectionAttemptId;

    @Column(name = "warning_index", nullable = false, updatable = false)
    private int warningIndex;

    protected CollectionWarningId() {}

    CollectionWarningId(UUID collectionAttemptId, int warningIndex) {
        this.collectionAttemptId = collectionAttemptId;
        this.warningIndex = warningIndex;
    }

    UUID collectionAttemptId() {
        return collectionAttemptId;
    }

    int warningIndex() {
        return warningIndex;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CollectionWarningId that)) {
            return false;
        }
        return warningIndex == that.warningIndex
                && Objects.equals(collectionAttemptId, that.collectionAttemptId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(collectionAttemptId, warningIndex);
    }
}
