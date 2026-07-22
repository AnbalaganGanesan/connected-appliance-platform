package com.example.connectedappliance.metrics.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

interface SpringDataMetricSampleRepository extends Repository<MetricSampleEntity, UUID> {

    @Query(
            value = """
                    SELECT sample FROM MetricSampleEntity sample
                    WHERE sample.applianceId = :applianceId
                      AND sample.observedAt >= :fromInclusive
                      AND sample.observedAt < :toExclusive
                    ORDER BY sample.observedAt ASC, sample.id ASC
                    """,
            countQuery = """
                    SELECT COUNT(sample) FROM MetricSampleEntity sample
                    WHERE sample.applianceId = :applianceId
                      AND sample.observedAt >= :fromInclusive
                      AND sample.observedAt < :toExclusive
                    """)
    Page<MetricSampleEntity> findHistory(
            @Param("applianceId") UUID applianceId,
            @Param("fromInclusive") Instant fromInclusive,
            @Param("toExclusive") Instant toExclusive,
            Pageable pageable);
}
