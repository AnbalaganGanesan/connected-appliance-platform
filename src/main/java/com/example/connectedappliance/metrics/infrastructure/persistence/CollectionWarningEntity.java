package com.example.connectedappliance.metrics.infrastructure.persistence;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "collection_warning")
class CollectionWarningEntity {

    @EmbeddedId
    private CollectionWarningId id;

    @Column(name = "code", nullable = false, length = 64, updatable = false)
    private String code;

    @Column(name = "message", nullable = false, length = 500, updatable = false)
    private String message;

    protected CollectionWarningEntity() {}

    CollectionWarningEntity(UUID collectionAttemptId, int warningIndex, String code, String message) {
        this.id = new CollectionWarningId(collectionAttemptId, warningIndex);
        this.code = code;
        this.message = message;
    }

    UUID collectionAttemptId() {
        return id.collectionAttemptId();
    }

    int warningIndex() {
        return id.warningIndex();
    }

    String code() {
        return code;
    }

    String message() {
        return message;
    }
}
