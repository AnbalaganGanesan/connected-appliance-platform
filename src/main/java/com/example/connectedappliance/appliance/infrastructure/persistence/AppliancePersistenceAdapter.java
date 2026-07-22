package com.example.connectedappliance.appliance.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.example.connectedappliance.appliance.application.port.out.AppliancePage;
import com.example.connectedappliance.appliance.application.port.out.AppliancePageRequest;
import com.example.connectedappliance.appliance.application.port.out.ApplianceRepository;
import com.example.connectedappliance.appliance.domain.Appliance;
import com.example.connectedappliance.appliance.domain.CollectionState;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Lazy
class AppliancePersistenceAdapter implements ApplianceRepository {

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
        ApplianceEntity entity = mapper.toEntity(appliance);
        entityManager.persist(entity);
        entityManager.flush();
        return mapper.toDomain(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Appliance> findById(UUID id) {
        Objects.requireNonNull(id, "id must not be null");
        return springDataRepository.findById(id).map(mapper::toDomain);
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
}
