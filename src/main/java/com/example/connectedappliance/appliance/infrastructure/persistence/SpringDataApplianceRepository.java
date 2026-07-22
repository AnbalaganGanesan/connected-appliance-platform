package com.example.connectedappliance.appliance.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.example.connectedappliance.appliance.domain.CollectionState;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataApplianceRepository extends JpaRepository<ApplianceEntity, UUID> {

    Page<ApplianceEntity> findByCollectionState(
            CollectionState collectionState, Pageable pageable);

    @Query(
            """
            SELECT appliance
            FROM ApplianceEntity appliance
            WHERE appliance.collectionState = :activeState
              AND appliance.nextCollectionDueAt <= :cutoffInclusive
            ORDER BY appliance.nextCollectionDueAt ASC, appliance.id ASC
            """)
    List<ApplianceEntity> findDue(
            @Param("activeState") CollectionState activeState,
            @Param("cutoffInclusive") Instant cutoffInclusive,
            Pageable pageable);
}
