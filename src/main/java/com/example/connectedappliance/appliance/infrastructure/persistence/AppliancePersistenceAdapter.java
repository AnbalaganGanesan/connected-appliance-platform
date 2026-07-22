package com.example.connectedappliance.appliance.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.example.connectedappliance.appliance.application.port.out.AppliancePage;
import com.example.connectedappliance.appliance.application.port.out.AppliancePageRequest;
import com.example.connectedappliance.appliance.application.port.out.ApplianceRepository;
import com.example.connectedappliance.appliance.application.port.in.ApplianceCollectionCommandPort;
import com.example.connectedappliance.appliance.application.port.in.ApplianceCollectionFinalizationCommand;
import com.example.connectedappliance.appliance.application.port.in.ApplianceCollectionFinalizationState;
import com.example.connectedappliance.appliance.application.port.in.ApplianceCollectionQueryPort;
import com.example.connectedappliance.appliance.application.port.in.ApplianceCollectionTarget;
import com.example.connectedappliance.appliance.application.exception.DuplicateApplianceException;
import com.example.connectedappliance.appliance.domain.Appliance;
import com.example.connectedappliance.appliance.domain.CollectionState;
import jakarta.persistence.EntityManager;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Lazy
class AppliancePersistenceAdapter implements
        ApplianceRepository, ApplianceCollectionQueryPort, ApplianceCollectionCommandPort {

    private static final String VENDOR_IDENTITY_CONSTRAINT =
            "uk_appliance_vendor_key_external_reference";

    private static final Sort LIST_SORT = Sort.by(
            Sort.Order.asc("createdAt"),
            Sort.Order.asc("id"));

    private final EntityManager entityManager;
    private final SpringDataApplianceRepository springDataRepository;
    private final AppliancePersistenceMapper mapper = new AppliancePersistenceMapper();

    AppliancePersistenceAdapter(
            EntityManager entityManager, SpringDataApplianceRepository springDataRepository) {
        this.entityManager = entityManager;
        this.springDataRepository = springDataRepository;
    }

    @Override
    @Transactional
    public Appliance insert(Appliance appliance) {
        Objects.requireNonNull(appliance, "appliance must not be null");
        try {
            ApplianceEntity entity = mapper.toEntity(appliance);
            entityManager.persist(entity);
            entityManager.flush();
            return mapper.toDomain(entity);
        } catch (RuntimeException exception) {
            if (causedByVendorIdentityConstraint(exception)) {
                throw new DuplicateApplianceException();
            }
            throw exception;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Appliance> findById(UUID id) {
        Objects.requireNonNull(id, "id must not be null");
        return springDataRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    @Transactional
    public Optional<Appliance> replaceMetadata(
            UUID id, String displayName, String description, Instant changedAt) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(displayName, "displayName must not be null");
        Objects.requireNonNull(changedAt, "changedAt must not be null");

        return springDataRepository.findByIdForUpdate(id).map(entity -> {
            Appliance current = mapper.toDomain(entity);
            Appliance replacement = current.replaceMetadata(displayName, description, changedAt);
            if (replacement == current) {
                return current;
            }

            entity.replaceMetadata(
                    replacement.displayName(),
                    replacement.description(),
                    replacement.updatedAt());
            entityManager.flush();
            return mapper.toDomain(entity);
        });
    }

    @Override
    @Transactional
    public Optional<Appliance> replaceCollectionInterval(
            UUID id, int collectionIntervalSeconds, Instant changedAt) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(changedAt, "changedAt must not be null");

        return springDataRepository.findByIdForUpdate(id).map(entity -> {
            Appliance current = mapper.toDomain(entity);
            Appliance replacement = current.replaceCollectionInterval(
                    collectionIntervalSeconds, changedAt);
            if (replacement == current) {
                return current;
            }

            entity.replaceCollectionInterval(
                    replacement.collectionIntervalSeconds(),
                    replacement.nextCollectionDueAt(),
                    replacement.updatedAt());
            entityManager.flush();
            return mapper.toDomain(entity);
        });
    }

    @Override
    @Transactional
    public Optional<Appliance> replaceCollectionState(
            UUID id, CollectionState collectionState, Instant changedAt) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(collectionState, "collectionState must not be null");
        Objects.requireNonNull(changedAt, "changedAt must not be null");

        return springDataRepository.findByIdForUpdate(id).map(entity -> {
            Appliance current = mapper.toDomain(entity);
            Appliance replacement = current.replaceCollectionState(collectionState, changedAt);
            if (replacement == current) {
                return current;
            }

            entity.replaceCollectionState(
                    replacement.collectionState(),
                    replacement.nextCollectionDueAt(),
                    replacement.updatedAt());
            entityManager.flush();
            return mapper.toDomain(entity);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public AppliancePage findAll(
            AppliancePageRequest pageRequest, Optional<CollectionState> collectionState) {
        Objects.requireNonNull(pageRequest, "pageRequest must not be null");
        Objects.requireNonNull(collectionState, "collectionState must not be null");

        PageRequest pageable = PageRequest.of(pageRequest.page(), pageRequest.size(), LIST_SORT);
        Page<ApplianceEntity> page = collectionState
                .map(state -> springDataRepository.findByCollectionState(state, pageable))
                .orElseGet(() -> springDataRepository.findAll(pageable));
        return new AppliancePage(
                page.getContent().stream().map(mapper::toDomain).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Appliance> findDue(Instant cutoffInclusive, int limit) {
        Objects.requireNonNull(cutoffInclusive, "cutoffInclusive must not be null");
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }

        return springDataRepository
                .findDue(
                        CollectionState.ACTIVE,
                        cutoffInclusive,
                        PageRequest.of(0, limit))
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ApplianceCollectionTarget> findCollectionTarget(UUID applianceId) {
        Objects.requireNonNull(applianceId, "applianceId must not be null");
        return springDataRepository.findById(applianceId).map(entity ->
                new ApplianceCollectionTarget(
                        entity.id(),
                        entity.collectionState(),
                        entity.vendorKey(),
                        entity.externalReference()));
    }

    @Override
    public Optional<ApplianceCollectionFinalizationState> lockForCollectionFinalization(
            UUID applianceId) {
        Objects.requireNonNull(applianceId, "applianceId must not be null");
        return springDataRepository.findByIdForUpdate(applianceId).map(this::toFinalizationState);
    }

    @Override
    public Optional<ApplianceCollectionFinalizationState> applyCollectionFinalization(
            ApplianceCollectionFinalizationCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return springDataRepository.findById(command.applianceId()).map(entity -> {
            Appliance current = mapper.toDomain(entity);
            Appliance replacement = current.finalizeCollection(
                    command.lastCollectionStatus(),
                    command.consecutiveFailureCount(),
                    command.nextCollectionDueAt(),
                    command.completedAt());
            entity.finalizeCollection(
                    replacement.consecutiveFailureCount(),
                    replacement.lastCollectionStatus(),
                    replacement.nextCollectionDueAt(),
                    replacement.updatedAt());
            entityManager.flush();
            return toFinalizationState(entity);
        });
    }

    private ApplianceCollectionFinalizationState toFinalizationState(ApplianceEntity entity) {
        return new ApplianceCollectionFinalizationState(
                entity.id(),
                entity.collectionState(),
                entity.collectionIntervalSeconds(),
                entity.consecutiveFailureCount(),
                entity.lastCollectionStatus(),
                entity.nextCollectionDueAt());
    }

    private boolean causedByVendorIdentityConstraint(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof ConstraintViolationException violation
                    && VENDOR_IDENTITY_CONSTRAINT.equals(violation.getConstraintName())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
