package com.example.connectedappliance.metrics.infrastructure.persistence;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.example.connectedappliance.metrics.application.port.out.CollectionAttemptPage;
import com.example.connectedappliance.metrics.application.port.out.CollectionAttemptQuery;
import com.example.connectedappliance.metrics.application.port.out.HistoryPageRequest;
import com.example.connectedappliance.metrics.application.port.out.MetricSamplePage;
import com.example.connectedappliance.metrics.application.port.out.MetricSampleQuery;
import com.example.connectedappliance.metrics.application.port.out.MetricsRepository;
import com.example.connectedappliance.metrics.domain.CollectionAttempt;
import com.example.connectedappliance.metrics.domain.CompletedCollection;
import jakarta.persistence.EntityManager;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Lazy
class MetricsPersistenceAdapter implements MetricsRepository {

    private final EntityManager entityManager;
    private final SpringDataCollectionAttemptRepository attemptRepository;
    private final SpringDataCollectionWarningRepository warningRepository;
    private final SpringDataMetricSampleRepository sampleRepository;
    private final MetricsPersistenceMapper mapper = new MetricsPersistenceMapper();

    MetricsPersistenceAdapter(
            EntityManager entityManager,
            SpringDataCollectionAttemptRepository attemptRepository,
            SpringDataCollectionWarningRepository warningRepository,
            SpringDataMetricSampleRepository sampleRepository) {
        this.entityManager = entityManager;
        this.attemptRepository = attemptRepository;
        this.warningRepository = warningRepository;
        this.sampleRepository = sampleRepository;
    }

    @Override
    @Transactional
    public CollectionAttempt insert(CompletedCollection completedCollection) {
        Objects.requireNonNull(completedCollection, "completedCollection must not be null");
        CollectionAttemptEntity attemptEntity = mapper.toAttemptEntity(completedCollection);
        List<CollectionWarningEntity> warningEntities =
                mapper.toWarningEntities(completedCollection);
        List<MetricSampleEntity> sampleEntities = mapper.toSampleEntities(completedCollection);

        entityManager.persist(attemptEntity);
        warningEntities.forEach(entityManager::persist);
        sampleEntities.forEach(entityManager::persist);
        entityManager.flush();

        return mapper.toDomain(attemptEntity, warningEntities);
    }

    @Override
    @Transactional(readOnly = true)
    public CollectionAttemptPage findAttempts(CollectionAttemptQuery query) {
        Objects.requireNonNull(query, "query must not be null");
        PageRequest pageable = pageRequest(query.pageRequest());
        Page<CollectionAttemptEntity> entityPage;
        if (query.trigger().isPresent() && query.outcome().isPresent()) {
            entityPage = attemptRepository.findHistoryByTriggerAndOutcome(
                    query.applianceId(), query.trigger().orElseThrow(), query.outcome().orElseThrow(), pageable);
        } else if (query.trigger().isPresent()) {
            entityPage = attemptRepository.findHistoryByTrigger(
                    query.applianceId(), query.trigger().orElseThrow(), pageable);
        } else if (query.outcome().isPresent()) {
            entityPage = attemptRepository.findHistoryByOutcome(
                    query.applianceId(), query.outcome().orElseThrow(), pageable);
        } else {
            entityPage = attemptRepository.findHistory(query.applianceId(), pageable);
        }

        Map<UUID, List<CollectionWarningEntity>> warningsByAttempt =
                loadWarnings(entityPage.getContent());
        List<CollectionAttempt> attempts = entityPage.getContent().stream()
                .map(entity -> mapper.toDomain(
                        entity, warningsByAttempt.getOrDefault(entity.id(), List.of())))
                .toList();
        return new CollectionAttemptPage(
                attempts,
                query.pageRequest().page(),
                query.pageRequest().size(),
                entityPage.getTotalElements(),
                entityPage.getTotalPages());
    }

    @Override
    @Transactional(readOnly = true)
    public MetricSamplePage findMetricSamples(MetricSampleQuery query) {
        Objects.requireNonNull(query, "query must not be null");
        Page<MetricSampleEntity> entityPage = sampleRepository.findHistory(
                query.applianceId(),
                query.fromInclusive(),
                query.toExclusive(),
                pageRequest(query.pageRequest()));
        return new MetricSamplePage(
                entityPage.getContent().stream().map(mapper::toDomain).toList(),
                query.pageRequest().page(),
                query.pageRequest().size(),
                entityPage.getTotalElements(),
                entityPage.getTotalPages());
    }

    private Map<UUID, List<CollectionWarningEntity>> loadWarnings(
            List<CollectionAttemptEntity> attempts) {
        if (attempts.isEmpty()) {
            return Map.of();
        }
        List<UUID> attemptIds = attempts.stream().map(CollectionAttemptEntity::id).toList();
        Map<UUID, List<CollectionWarningEntity>> grouped = new LinkedHashMap<>();
        for (CollectionWarningEntity warning : warningRepository.findForAttempts(attemptIds)) {
            grouped.computeIfAbsent(warning.collectionAttemptId(), ignored -> new ArrayList<>())
                    .add(warning);
        }
        return grouped;
    }

    private PageRequest pageRequest(HistoryPageRequest request) {
        return PageRequest.of(request.page(), request.size());
    }
}
