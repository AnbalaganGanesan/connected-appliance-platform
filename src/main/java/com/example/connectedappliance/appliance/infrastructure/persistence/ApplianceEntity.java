package com.example.connectedappliance.appliance.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

import com.example.connectedappliance.appliance.domain.CollectionState;
import com.example.connectedappliance.appliance.domain.LastCollectionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "appliance")
class ApplianceEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "vendor_key", nullable = false, length = 50, updatable = false)
    private String vendorKey;

    @Column(name = "external_reference", nullable = false, length = 128, updatable = false)
    private String externalReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "collection_state", nullable = false, length = 16)
    private CollectionState collectionState;

    @Column(name = "collection_interval_seconds", nullable = false)
    private int collectionIntervalSeconds;

    @Column(name = "next_collection_due_at")
    private Instant nextCollectionDueAt;

    @Column(name = "consecutive_failure_count", nullable = false)
    private int consecutiveFailureCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_collection_status", nullable = false, length = 20)
    private LastCollectionStatus lastCollectionStatus;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ApplianceEntity() {}

    ApplianceEntity(
            UUID id,
            String displayName,
            String description,
            String vendorKey,
            String externalReference,
            CollectionState collectionState,
            int collectionIntervalSeconds,
            Instant nextCollectionDueAt,
            int consecutiveFailureCount,
            LastCollectionStatus lastCollectionStatus,
            long version,
            Instant createdAt,
            Instant updatedAt) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.vendorKey = vendorKey;
        this.externalReference = externalReference;
        this.collectionState = collectionState;
        this.collectionIntervalSeconds = collectionIntervalSeconds;
        this.nextCollectionDueAt = nextCollectionDueAt;
        this.consecutiveFailureCount = consecutiveFailureCount;
        this.lastCollectionStatus = lastCollectionStatus;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    UUID id() {
        return id;
    }

    String displayName() {
        return displayName;
    }

    String description() {
        return description;
    }

    String vendorKey() {
        return vendorKey;
    }

    String externalReference() {
        return externalReference;
    }

    CollectionState collectionState() {
        return collectionState;
    }

    int collectionIntervalSeconds() {
        return collectionIntervalSeconds;
    }

    Instant nextCollectionDueAt() {
        return nextCollectionDueAt;
    }

    int consecutiveFailureCount() {
        return consecutiveFailureCount;
    }

    LastCollectionStatus lastCollectionStatus() {
        return lastCollectionStatus;
    }

    long version() {
        return version;
    }

    Instant createdAt() {
        return createdAt;
    }

    Instant updatedAt() {
        return updatedAt;
    }
}
