package com.example.connectedappliance.metrics.infrastructure.persistence;

import java.util.UUID;

import com.example.connectedappliance.metrics.domain.CollectionOutcome;
import com.example.connectedappliance.metrics.domain.CollectionTrigger;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

interface SpringDataCollectionAttemptRepository
        extends Repository<CollectionAttemptEntity, UUID> {

    @Query(
            value = """
                    SELECT attempt FROM CollectionAttemptEntity attempt
                    WHERE attempt.applianceId = :applianceId
                    ORDER BY attempt.startedAt DESC, attempt.id ASC
                    """,
            countQuery = """
                    SELECT COUNT(attempt) FROM CollectionAttemptEntity attempt
                    WHERE attempt.applianceId = :applianceId
                    """)
    Page<CollectionAttemptEntity> findHistory(
            @Param("applianceId") UUID applianceId, Pageable pageable);

    @Query(
            value = """
                    SELECT attempt FROM CollectionAttemptEntity attempt
                    WHERE attempt.applianceId = :applianceId
                      AND attempt.trigger = :trigger
                    ORDER BY attempt.startedAt DESC, attempt.id ASC
                    """,
            countQuery = """
                    SELECT COUNT(attempt) FROM CollectionAttemptEntity attempt
                    WHERE attempt.applianceId = :applianceId
                      AND attempt.trigger = :trigger
                    """)
    Page<CollectionAttemptEntity> findHistoryByTrigger(
            @Param("applianceId") UUID applianceId,
            @Param("trigger") CollectionTrigger trigger,
            Pageable pageable);

    @Query(
            value = """
                    SELECT attempt FROM CollectionAttemptEntity attempt
                    WHERE attempt.applianceId = :applianceId
                      AND attempt.outcome = :outcome
                    ORDER BY attempt.startedAt DESC, attempt.id ASC
                    """,
            countQuery = """
                    SELECT COUNT(attempt) FROM CollectionAttemptEntity attempt
                    WHERE attempt.applianceId = :applianceId
                      AND attempt.outcome = :outcome
                    """)
    Page<CollectionAttemptEntity> findHistoryByOutcome(
            @Param("applianceId") UUID applianceId,
            @Param("outcome") CollectionOutcome outcome,
            Pageable pageable);

    @Query(
            value = """
                    SELECT attempt FROM CollectionAttemptEntity attempt
                    WHERE attempt.applianceId = :applianceId
                      AND attempt.trigger = :trigger
                      AND attempt.outcome = :outcome
                    ORDER BY attempt.startedAt DESC, attempt.id ASC
                    """,
            countQuery = """
                    SELECT COUNT(attempt) FROM CollectionAttemptEntity attempt
                    WHERE attempt.applianceId = :applianceId
                      AND attempt.trigger = :trigger
                      AND attempt.outcome = :outcome
                    """)
    Page<CollectionAttemptEntity> findHistoryByTriggerAndOutcome(
            @Param("applianceId") UUID applianceId,
            @Param("trigger") CollectionTrigger trigger,
            @Param("outcome") CollectionOutcome outcome,
            Pageable pageable);
}
