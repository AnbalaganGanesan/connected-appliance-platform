package com.example.connectedappliance.integration;

import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Execution(ExecutionMode.SAME_THREAD)
class ApplianceSchemaIT extends PostgresIntegrationTestSupport {

    private static final OffsetDateTime CREATED_AT =
            OffsetDateTime.of(2026, 7, 21, 10, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime DUE_AT = CREATED_AT.plusSeconds(30);

    private static final List<String> EXPECTED_COLUMNS = List.of(
            "id",
            "display_name",
            "description",
            "vendor_key",
            "external_reference",
            "collection_state",
            "collection_interval_seconds",
            "next_collection_due_at",
            "consecutive_failure_count",
            "last_collection_status",
            "version",
            "created_at",
            "updated_at");

    private static final Set<String> EXPECTED_CONSTRAINTS = Set.of(
            "pk_appliance",
            "uk_appliance_vendor_key_external_reference",
            "ck_appliance_display_name_non_blank",
            "ck_appliance_vendor_key_format",
            "ck_appliance_external_reference_non_blank",
            "ck_appliance_collection_interval_seconds",
            "ck_appliance_collection_state",
            "ck_appliance_last_collection_status",
            "ck_appliance_consecutive_failure_count",
            "ck_appliance_version",
            "ck_appliance_updated_at_not_before_created_at",
            "ck_appliance_collection_state_due");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Flyway flyway;

    @BeforeEach
    @AfterEach
    void removeApplianceRows() {
        jdbcTemplate.update("DELETE FROM appliance");
    }

    @Test
    void appliesFlywayV1AndV2AndRetainsTheApprovedApplianceTable() {
        assertThatCode(flyway::validate).doesNotThrowAnyException();

        MigrationInfo[] applied = flyway.info().applied();
        assertThat(applied)
                .extracting(info -> info.getVersion().toString())
                .containsExactly("1", "2");
        assertThat(applied)
                .extracting(MigrationInfo::getDescription)
                .containsExactly("create appliance", "create metric collection schema");
        assertThat(applied)
                .allMatch(info -> info.getState().name().equals("SUCCESS"));
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(flyway.info().all())
                .noneMatch(info -> info.getState().name().contains("FAILED"));

        List<String> applicationTables = jdbcTemplate.queryForList(
                """
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name <> 'flyway_schema_history'
                ORDER BY table_name
                """,
                String.class);
        assertThat(applicationTables)
                .containsExactly(
                        "appliance",
                        "collection_attempt",
                        "collection_warning",
                        "metric_sample");
    }

    @Test
    void definesTheExactColumnsTypesNullabilityAndDefaults() {
        List<ColumnMetadata> columns = applianceColumns();

        assertThat(columns)
                .extracting(ColumnMetadata::name)
                .containsExactlyElementsOf(EXPECTED_COLUMNS);
        assertThat(columns).hasSize(13);

        Map<String, ColumnMetadata> byName = columns.stream()
                .collect(Collectors.toMap(ColumnMetadata::name, Function.identity()));

        assertColumn(byName, "id", "uuid", null, false);
        assertColumn(byName, "display_name", "character varying", 100, false);
        assertColumn(byName, "description", "character varying", 500, true);
        assertColumn(byName, "vendor_key", "character varying", 50, false);
        assertColumn(byName, "external_reference", "character varying", 128, false);
        assertColumn(byName, "collection_state", "character varying", 16, false);
        assertColumn(byName, "collection_interval_seconds", "integer", null, false);
        assertColumn(byName, "next_collection_due_at", "timestamp with time zone", null, true);
        assertColumn(byName, "consecutive_failure_count", "integer", null, false);
        assertColumn(byName, "last_collection_status", "character varying", 20, false);
        assertColumn(byName, "version", "bigint", null, false);
        assertColumn(byName, "created_at", "timestamp with time zone", null, false);
        assertColumn(byName, "updated_at", "timestamp with time zone", null, false);

        assertThat(columns)
                .filteredOn(ColumnMetadata::nullable)
                .extracting(ColumnMetadata::name)
                .containsExactly("description", "next_collection_due_at");
        assertThat(columns)
                .filteredOn(column -> column.defaultExpression() != null)
                .extracting(ColumnMetadata::name)
                .containsExactly(
                        "collection_state",
                        "consecutive_failure_count",
                        "last_collection_status",
                        "version");
        assertThat(byName.get("collection_state").defaultExpression()).contains("'ACTIVE'");
        assertThat(byName.get("consecutive_failure_count").defaultExpression()).isEqualTo("0");
        assertThat(byName.get("last_collection_status").defaultExpression())
                .contains("'NEVER_ATTEMPTED'");
        assertThat(byName.get("version").defaultExpression()).isEqualTo("0");
    }

    @Test
    void appliesOnlyTheApprovedBusinessDefaults() {
        UUID id = UUID.randomUUID();
        String externalReference = uniqueReference("defaults");

        jdbcTemplate.update(
                """
                INSERT INTO appliance (
                    id,
                    display_name,
                    vendor_key,
                    external_reference,
                    collection_interval_seconds,
                    next_collection_due_at,
                    created_at,
                    updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                "Defaulted appliance",
                "mock-alpha",
                externalReference,
                30,
                DUE_AT,
                CREATED_AT,
                CREATED_AT);

        Map<String, Object> stored = jdbcTemplate.queryForMap(
                """
                SELECT description,
                       collection_state,
                       consecutive_failure_count,
                       last_collection_status,
                       version
                FROM appliance
                WHERE id = ?
                """,
                id);

        assertThat(stored.get("description")).isNull();
        assertThat(stored.get("collection_state")).isEqualTo("ACTIVE");
        assertThat(((Number) stored.get("consecutive_failure_count")).intValue()).isZero();
        assertThat(stored.get("last_collection_status")).isEqualTo("NEVER_ATTEMPTED");
        assertThat(((Number) stored.get("version")).longValue()).isZero();
    }

    @Test
    void enforcesPrimaryKeyAndCaseSensitiveRegistrationUniqueness() {
        UUID id = UUID.randomUUID();
        ApplianceRow first = validRow("CaseSensitiveRef").withId(id);
        insert(first);

        assertSqlState("23502", () -> insert(validRow().withId(null)));
        assertSqlState(
                "23505", () -> insert(validRow("different-reference").withId(id)));
        assertSqlState(
                "23505",
                () -> insert(validRow("CaseSensitiveRef").withVendorKey("mock-alpha")));

        insert(validRow("casesensitiveref").withVendorKey("mock-alpha"));
        insert(validRow("CaseSensitiveRef").withVendorKey("mock-beta"));

        assertThat(applianceCount()).isEqualTo(3);
    }

    @Test
    void enforcesDisplayVendorKeyAndExternalReferenceChecks() {
        assertSqlState("23514", () -> insert(validRow().withDisplayName("")));
        assertSqlState("23514", () -> insert(validRow().withDisplayName("   ")));
        insert(validRow().withDisplayName("Valid display name"));

        for (String vendorKey : List.of("mock-alpha", "vendor1", "vendor-2")) {
            insert(validRow().withVendorKey(vendorKey));
        }
        for (String vendorKey : List.of("Mock-Alpha", "vendor_key", "vendor key", "vendor.key", "")) {
            assertSqlState("23514", () -> insert(validRow().withVendorKey(vendorKey)));
        }

        assertSqlState("23514", () -> insert(validRow("")));
        assertSqlState("23514", () -> insert(validRow("   ")));

        UUID opaqueId = UUID.randomUUID();
        String opaqueReference = "  Device/Aa-001  ";
        insert(validRow(opaqueReference).withId(opaqueId));
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT external_reference FROM appliance WHERE id = ?",
                        String.class,
                        opaqueId))
                .isEqualTo(opaqueReference);
    }

    @Test
    void enforcesInclusiveCollectionIntervalBoundsAndNullability() {
        insert(validRow().withCollectionIntervalSeconds(5));
        insert(validRow().withCollectionIntervalSeconds(86400));

        assertSqlState("23514", () -> insert(validRow().withCollectionIntervalSeconds(4)));
        assertSqlState("23514", () -> insert(validRow().withCollectionIntervalSeconds(86401)));
        assertSqlState("23502", () -> insert(validRow().withCollectionIntervalSeconds(null)));
    }

    @Test
    void enforcesOperationalValueAllowLists() {
        insert(validRow().withCollectionState("ACTIVE").withNextCollectionDueAt(DUE_AT));
        insert(validRow().withCollectionState("PAUSED").withNextCollectionDueAt(null));

        for (String state : List.of("active", "DISABLED", "")) {
            assertSqlState("23514", () -> insert(validRow().withCollectionState(state)));
        }

        for (String status :
                List.of("NEVER_ATTEMPTED", "SUCCESS", "PARTIAL_SUCCESS", "FAILED")) {
            insert(validRow().withLastCollectionStatus(status));
        }
        for (String status : List.of("PARTIAL", "TIMEOUT", "")) {
            assertSqlState("23514", () -> insert(validRow().withLastCollectionStatus(status)));
        }
    }

    @Test
    void enforcesActiveAndPausedDueStateInvariant() {
        insert(validRow().withCollectionState("ACTIVE").withNextCollectionDueAt(DUE_AT));
        insert(validRow().withCollectionState("PAUSED").withNextCollectionDueAt(null));

        assertSqlState(
                "23514",
                () -> insert(validRow()
                        .withCollectionState("ACTIVE")
                        .withNextCollectionDueAt(null)));
        assertSqlState(
                "23514",
                () -> insert(validRow()
                        .withCollectionState("PAUSED")
                        .withNextCollectionDueAt(DUE_AT)));
    }

    @Test
    void enforcesCounterVersionAndTimestampChecks() {
        insert(validRow().withConsecutiveFailureCount(0));
        insert(validRow().withConsecutiveFailureCount(3));
        assertSqlState("23514", () -> insert(validRow().withConsecutiveFailureCount(-1)));

        insert(validRow().withVersion(0L));
        insert(validRow().withVersion(4L));
        assertSqlState("23514", () -> insert(validRow().withVersion(-1L)));

        insert(validRow().withUpdatedAt(CREATED_AT));
        insert(validRow().withUpdatedAt(CREATED_AT.plusSeconds(1)));
        assertSqlState(
                "23514", () -> insert(validRow().withUpdatedAt(CREATED_AT.minusNanos(1_000))));
    }

    @Test
    void createsExactlyTheApprovedNamedConstraints() {
        List<String> constraintNames = jdbcTemplate.queryForList(
                """
                SELECT constraint_definition.conname
                FROM pg_constraint constraint_definition
                JOIN pg_class appliance_table
                  ON appliance_table.oid = constraint_definition.conrelid
                JOIN pg_namespace appliance_schema
                  ON appliance_schema.oid = appliance_table.relnamespace
                WHERE appliance_schema.nspname = 'public'
                  AND appliance_table.relname = 'appliance'
                ORDER BY constraint_definition.conname
                """,
                String.class);

        assertThat(constraintNames).containsExactlyInAnyOrderElementsOf(EXPECTED_CONSTRAINTS);
        assertThat(constraintNames).hasSize(12);
    }

    @Test
    void createsExactlyTheApprovedIndexesWithOrderedColumnsAndPartialDuePredicate() {
        Map<String, IndexMetadata> indexes = applianceIndexes().stream()
                .collect(Collectors.toMap(IndexMetadata::name, Function.identity()));

        assertThat(indexes.keySet())
                .containsExactlyInAnyOrder(
                        "pk_appliance",
                        "uk_appliance_vendor_key_external_reference",
                        "idx_appliance_created_at_id",
                        "idx_appliance_collection_state_created_at_id",
                        "idx_appliance_active_due_at_id");

        assertIndex(indexes, "pk_appliance", List.of("id"), true, true, false);
        assertIndex(
                indexes,
                "uk_appliance_vendor_key_external_reference",
                List.of("vendor_key", "external_reference"),
                false,
                true,
                false);
        assertIndex(
                indexes,
                "idx_appliance_created_at_id",
                List.of("created_at", "id"),
                false,
                false,
                false);
        assertIndex(
                indexes,
                "idx_appliance_collection_state_created_at_id",
                List.of("collection_state", "created_at", "id"),
                false,
                false,
                false);
        assertIndex(
                indexes,
                "idx_appliance_active_due_at_id",
                List.of("next_collection_due_at", "id"),
                false,
                false,
                true);

        IndexMetadata dueIndex = indexes.get("idx_appliance_active_due_at_id");
        assertThat(dueIndex.predicate()).contains("collection_state").contains("ACTIVE");
        assertThat(indexes.values())
                .filteredOn(index -> !index.unique())
                .extracting(IndexMetadata::name)
                .containsExactlyInAnyOrder(
                        "idx_appliance_created_at_id",
                        "idx_appliance_collection_state_created_at_id",
                        "idx_appliance_active_due_at_id");
    }

    private List<ColumnMetadata> applianceColumns() {
        return jdbcTemplate.query(
                """
                SELECT column_name,
                       data_type,
                       character_maximum_length,
                       is_nullable,
                       column_default
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'appliance'
                ORDER BY ordinal_position
                """,
                (resultSet, rowNumber) -> new ColumnMetadata(
                        resultSet.getString("column_name"),
                        resultSet.getString("data_type"),
                        resultSet.getObject("character_maximum_length", Integer.class),
                        resultSet.getString("is_nullable").equals("YES"),
                        resultSet.getString("column_default")));
    }

    private List<IndexMetadata> applianceIndexes() {
        return jdbcTemplate.query(
                """
                SELECT index_table.relname AS index_name,
                       index_definition.indisprimary AS primary_index,
                       index_definition.indisunique AS unique_index,
                       index_definition.indpred IS NOT NULL AS partial_index,
                       pg_get_expr(
                           index_definition.indpred,
                           index_definition.indrelid
                       ) AS predicate,
                       index_definition.indoption::text AS sort_options,
                       array_agg(
                           table_column.attname
                           ORDER BY indexed_key.ordinality
                       ) AS indexed_columns
                FROM pg_index index_definition
                JOIN pg_class appliance_table
                  ON appliance_table.oid = index_definition.indrelid
                JOIN pg_namespace appliance_schema
                  ON appliance_schema.oid = appliance_table.relnamespace
                JOIN pg_class index_table
                  ON index_table.oid = index_definition.indexrelid
                CROSS JOIN LATERAL unnest(
                    index_definition.indkey::smallint[]
                ) WITH ORDINALITY AS indexed_key(attribute_number, ordinality)
                JOIN pg_attribute table_column
                  ON table_column.attrelid = appliance_table.oid
                 AND table_column.attnum = indexed_key.attribute_number
                WHERE appliance_schema.nspname = 'public'
                  AND appliance_table.relname = 'appliance'
                GROUP BY index_table.relname,
                         index_definition.indisprimary,
                         index_definition.indisunique,
                         index_definition.indpred,
                         index_definition.indrelid,
                         index_definition.indoption
                ORDER BY index_table.relname
                """,
                (resultSet, rowNumber) -> {
                    Object[] indexedColumns =
                            (Object[]) resultSet.getArray("indexed_columns").getArray();
                    return new IndexMetadata(
                            resultSet.getString("index_name"),
                            resultSet.getBoolean("primary_index"),
                            resultSet.getBoolean("unique_index"),
                            resultSet.getBoolean("partial_index"),
                            resultSet.getString("predicate"),
                            parseSortOptions(resultSet.getString("sort_options")),
                            Arrays.stream(indexedColumns).map(Object::toString).toList());
                });
    }

    private List<Integer> parseSortOptions(String sortOptions) {
        if (sortOptions == null || sortOptions.isBlank()) {
            return List.of();
        }
        return Arrays.stream(sortOptions.trim().split("\\s+"))
                .map(Integer::valueOf)
                .toList();
    }

    private void assertColumn(
            Map<String, ColumnMetadata> columns,
            String name,
            String dataType,
            Integer maximumLength,
            boolean nullable) {
        ColumnMetadata column = columns.get(name);
        assertThat(column).isNotNull();
        assertThat(column.dataType()).isEqualTo(dataType);
        assertThat(column.maximumLength()).isEqualTo(maximumLength);
        assertThat(column.nullable()).isEqualTo(nullable);
    }

    private void assertIndex(
            Map<String, IndexMetadata> indexes,
            String name,
            List<String> columns,
            boolean primary,
            boolean unique,
            boolean partial) {
        IndexMetadata index = indexes.get(name);
        assertThat(index).isNotNull();
        assertThat(index.columns()).containsExactlyElementsOf(columns);
        assertThat(index.primary()).isEqualTo(primary);
        assertThat(index.unique()).isEqualTo(unique);
        assertThat(index.partial()).isEqualTo(partial);
        assertThat(index.sortOptions()).containsOnly(0);
        if (!partial) {
            assertThat(index.predicate()).isNull();
        }
    }

    private void assertSqlState(String expectedSqlState, ThrowingCallable operation) {
        Throwable failure = catchThrowable(operation);
        assertThat(failure).isNotNull();
        assertThat(findSqlException(failure).getSQLState()).isEqualTo(expectedSqlState);
    }

    private SQLException findSqlException(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof SQLException sqlException) {
                return sqlException;
            }
            current = current.getCause();
        }
        throw new AssertionError("Expected a database integrity exception");
    }

    private long applianceCount() {
        Long count = jdbcTemplate.queryForObject("SELECT count(*) FROM appliance", Long.class);
        return count == null ? 0 : count;
    }

    private int insert(ApplianceRow row) {
        return jdbcTemplate.update(
                """
                INSERT INTO appliance (
                    id,
                    display_name,
                    description,
                    vendor_key,
                    external_reference,
                    collection_state,
                    collection_interval_seconds,
                    next_collection_due_at,
                    consecutive_failure_count,
                    last_collection_status,
                    version,
                    created_at,
                    updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                row.id(),
                row.displayName(),
                row.description(),
                row.vendorKey(),
                row.externalReference(),
                row.collectionState(),
                row.collectionIntervalSeconds(),
                row.nextCollectionDueAt(),
                row.consecutiveFailureCount(),
                row.lastCollectionStatus(),
                row.version(),
                row.createdAt(),
                row.updatedAt());
    }

    private ApplianceRow validRow() {
        return validRow(uniqueReference("appliance"));
    }

    private ApplianceRow validRow(String externalReference) {
        return new ApplianceRow(
                UUID.randomUUID(),
                "Schema test appliance",
                null,
                "mock-alpha",
                externalReference,
                "ACTIVE",
                30,
                DUE_AT,
                0,
                "NEVER_ATTEMPTED",
                0L,
                CREATED_AT,
                CREATED_AT);
    }

    private String uniqueReference(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private record ColumnMetadata(
            String name,
            String dataType,
            Integer maximumLength,
            boolean nullable,
            String defaultExpression) {}

    private record IndexMetadata(
            String name,
            boolean primary,
            boolean unique,
            boolean partial,
            String predicate,
            List<Integer> sortOptions,
            List<String> columns) {}

    private record ApplianceRow(
            UUID id,
            String displayName,
            String description,
            String vendorKey,
            String externalReference,
            String collectionState,
            Integer collectionIntervalSeconds,
            OffsetDateTime nextCollectionDueAt,
            Integer consecutiveFailureCount,
            String lastCollectionStatus,
            Long version,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {

        ApplianceRow withId(UUID newId) {
            return new ApplianceRow(
                    newId,
                    displayName,
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
                    updatedAt);
        }

        ApplianceRow withDisplayName(String newDisplayName) {
            return new ApplianceRow(
                    id,
                    newDisplayName,
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
                    updatedAt);
        }

        ApplianceRow withVendorKey(String newVendorKey) {
            return new ApplianceRow(
                    id,
                    displayName,
                    description,
                    newVendorKey,
                    externalReference,
                    collectionState,
                    collectionIntervalSeconds,
                    nextCollectionDueAt,
                    consecutiveFailureCount,
                    lastCollectionStatus,
                    version,
                    createdAt,
                    updatedAt);
        }

        ApplianceRow withCollectionState(String newCollectionState) {
            return new ApplianceRow(
                    id,
                    displayName,
                    description,
                    vendorKey,
                    externalReference,
                    newCollectionState,
                    collectionIntervalSeconds,
                    nextCollectionDueAt,
                    consecutiveFailureCount,
                    lastCollectionStatus,
                    version,
                    createdAt,
                    updatedAt);
        }

        ApplianceRow withCollectionIntervalSeconds(Integer newInterval) {
            return new ApplianceRow(
                    id,
                    displayName,
                    description,
                    vendorKey,
                    externalReference,
                    collectionState,
                    newInterval,
                    nextCollectionDueAt,
                    consecutiveFailureCount,
                    lastCollectionStatus,
                    version,
                    createdAt,
                    updatedAt);
        }

        ApplianceRow withNextCollectionDueAt(OffsetDateTime newDueAt) {
            return new ApplianceRow(
                    id,
                    displayName,
                    description,
                    vendorKey,
                    externalReference,
                    collectionState,
                    collectionIntervalSeconds,
                    newDueAt,
                    consecutiveFailureCount,
                    lastCollectionStatus,
                    version,
                    createdAt,
                    updatedAt);
        }

        ApplianceRow withConsecutiveFailureCount(Integer newFailureCount) {
            return new ApplianceRow(
                    id,
                    displayName,
                    description,
                    vendorKey,
                    externalReference,
                    collectionState,
                    collectionIntervalSeconds,
                    nextCollectionDueAt,
                    newFailureCount,
                    lastCollectionStatus,
                    version,
                    createdAt,
                    updatedAt);
        }

        ApplianceRow withLastCollectionStatus(String newLastCollectionStatus) {
            return new ApplianceRow(
                    id,
                    displayName,
                    description,
                    vendorKey,
                    externalReference,
                    collectionState,
                    collectionIntervalSeconds,
                    nextCollectionDueAt,
                    consecutiveFailureCount,
                    newLastCollectionStatus,
                    version,
                    createdAt,
                    updatedAt);
        }

        ApplianceRow withVersion(Long newVersion) {
            return new ApplianceRow(
                    id,
                    displayName,
                    description,
                    vendorKey,
                    externalReference,
                    collectionState,
                    collectionIntervalSeconds,
                    nextCollectionDueAt,
                    consecutiveFailureCount,
                    lastCollectionStatus,
                    newVersion,
                    createdAt,
                    updatedAt);
        }

        ApplianceRow withUpdatedAt(OffsetDateTime newUpdatedAt) {
            return new ApplianceRow(
                    id,
                    displayName,
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
                    newUpdatedAt);
        }
    }
}
