package com.example.connectedappliance.appliance.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/** Vendor-neutral appliance aggregate state used for construction and persistence reconstruction. */
public final class Appliance {

    private static final int MAX_DISPLAY_NAME_LENGTH = 100;
    private static final int MAX_DESCRIPTION_LENGTH = 500;
    private static final int MAX_VENDOR_KEY_LENGTH = 50;
    private static final int MAX_EXTERNAL_REFERENCE_LENGTH = 128;
    private static final int MIN_COLLECTION_INTERVAL_SECONDS = 5;
    private static final int MAX_COLLECTION_INTERVAL_SECONDS = 86_400;
    private static final Pattern VENDOR_KEY_PATTERN = Pattern.compile("^[a-z0-9-]+$");

    private final UUID id;
    private final String displayName;
    private final String description;
    private final String vendorKey;
    private final String externalReference;
    private final CollectionState collectionState;
    private final int collectionIntervalSeconds;
    private final Instant nextCollectionDueAt;
    private final int consecutiveFailureCount;
    private final LastCollectionStatus lastCollectionStatus;
    private final long version;
    private final Instant createdAt;
    private final Instant updatedAt;

    public Appliance(
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
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.displayName = requireNonBlank(displayName, "displayName");
        requireMaximumLength(this.displayName, MAX_DISPLAY_NAME_LENGTH, "displayName");
        requireMaximumLength(description, MAX_DESCRIPTION_LENGTH, "description");
        this.description = description;
        this.vendorKey = Objects.requireNonNull(vendorKey, "vendorKey must not be null");
        requireMaximumLength(this.vendorKey, MAX_VENDOR_KEY_LENGTH, "vendorKey");
        if (!VENDOR_KEY_PATTERN.matcher(this.vendorKey).matches()) {
            throw new IllegalArgumentException("vendorKey has an invalid format");
        }
        this.externalReference = requireNonBlank(externalReference, "externalReference");
        requireMaximumLength(
                this.externalReference, MAX_EXTERNAL_REFERENCE_LENGTH, "externalReference");
        this.collectionState =
                Objects.requireNonNull(collectionState, "collectionState must not be null");
        if (collectionIntervalSeconds < MIN_COLLECTION_INTERVAL_SECONDS
                || collectionIntervalSeconds > MAX_COLLECTION_INTERVAL_SECONDS) {
            throw new IllegalArgumentException("collectionIntervalSeconds is out of range");
        }
        this.collectionIntervalSeconds = collectionIntervalSeconds;
        if (consecutiveFailureCount < 0) {
            throw new IllegalArgumentException("consecutiveFailureCount must not be negative");
        }
        this.consecutiveFailureCount = consecutiveFailureCount;
        this.lastCollectionStatus = Objects.requireNonNull(
                lastCollectionStatus, "lastCollectionStatus must not be null");
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (this.updatedAt.isBefore(this.createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
        if (this.collectionState == CollectionState.ACTIVE && nextCollectionDueAt == null) {
            throw new IllegalArgumentException("ACTIVE appliances require a next collection due time");
        }
        if (this.collectionState == CollectionState.PAUSED && nextCollectionDueAt != null) {
            throw new IllegalArgumentException("PAUSED appliances must not have a next collection due time");
        }
        this.nextCollectionDueAt = nextCollectionDueAt;
    }

    public UUID id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    public String vendorKey() {
        return vendorKey;
    }

    public String externalReference() {
        return externalReference;
    }

    public CollectionState collectionState() {
        return collectionState;
    }

    public int collectionIntervalSeconds() {
        return collectionIntervalSeconds;
    }

    public Instant nextCollectionDueAt() {
        return nextCollectionDueAt;
    }

    public int consecutiveFailureCount() {
        return consecutiveFailureCount;
    }

    public LastCollectionStatus lastCollectionStatus() {
        return lastCollectionStatus;
    }

    public long version() {
        return version;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    /** Replaces display metadata while preserving all identity and collection state. */
    public Appliance replaceMetadata(
            String displayName, String description, Instant changedAt) {
        String validatedDisplayName = requireNonBlank(displayName, "displayName");
        requireMaximumLength(validatedDisplayName, MAX_DISPLAY_NAME_LENGTH, "displayName");
        requireMaximumLength(description, MAX_DESCRIPTION_LENGTH, "description");
        Objects.requireNonNull(changedAt, "changedAt must not be null");

        if (this.displayName.equals(validatedDisplayName)
                && Objects.equals(this.description, description)) {
            return this;
        }

        return new Appliance(
                id,
                validatedDisplayName,
                description,
                vendorKey,
                externalReference,
                collectionState,
                collectionIntervalSeconds,
                nextCollectionDueAt,
                consecutiveFailureCount,
                lastCollectionStatus,
                version,
                createdAt,
                changedAt);
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    private static void requireMaximumLength(String value, int maximumLength, String fieldName) {
        if (value != null && value.length() > maximumLength) {
            throw new IllegalArgumentException(fieldName + " exceeds its maximum length");
        }
    }
}
