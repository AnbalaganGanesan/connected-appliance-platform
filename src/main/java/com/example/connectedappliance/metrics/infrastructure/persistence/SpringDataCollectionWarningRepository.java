package com.example.connectedappliance.metrics.infrastructure.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

interface SpringDataCollectionWarningRepository
        extends Repository<CollectionWarningEntity, CollectionWarningId> {

    @Query("""
            SELECT warning FROM CollectionWarningEntity warning
            WHERE warning.id.collectionAttemptId IN :attemptIds
            ORDER BY warning.id.collectionAttemptId ASC, warning.id.warningIndex ASC
            """)
    List<CollectionWarningEntity> findForAttempts(@Param("attemptIds") List<UUID> attemptIds);
}
