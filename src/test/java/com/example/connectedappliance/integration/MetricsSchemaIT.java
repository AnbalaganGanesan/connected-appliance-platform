package com.example.connectedappliance.integration;

import java.math.BigDecimal;
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
class MetricsSchemaIT extends PostgresIntegrationTestSupport {

    private static final OffsetDateTime STARTED_AT =
            OffsetDateTime.of(2026, 7, 21, 10, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime COMPLETED_AT = STARTED_AT.plusSeconds(2);
    private static final OffsetDateTime DUE_AT = COMPLETED_AT.plusSeconds(30);

    private static final List<String> ATTEMPT_COLUMNS = List.of(
            "id",
            "appliance_id",
            "trigger",
            "outcome",
            "started_at",
            "completed_at",
            "sample_count",
            "failure_category",
            "failure_message",
            "retry_after_seconds",
            "next_collection_due_at");

    private static final Set<String> ATTEMPT_CONSTRAINTS = Set.of(
            "pk_collection_attempt",
            "fk_collection_attempt_appliance",
            "uk_collection_attempt_id_appliance_id",
            "ck_collection_attempt_trigger",
            "ck_collection_attempt_outcome",
            "ck_collection_attempt_completed_at_not_before_started_at",
            "ck_collection_attempt_sample_count_non_negative",
            "ck_collection_attempt_outcome_sample_count",
            "ck_collection_attempt_failure_category",
            "ck_collection_attempt_failure_fields",
            "ck_collection_attempt_retry_after_seconds",
            "ck_collection_attempt_next_due_not_before_completed_at");

    private static final List<String> WARNING_COLUMNS =
            List.of("collection_attempt_id", "warning_index", "code", "message");

    private static final Set<String> WARNING_CONSTRAINTS = Set.of(
            "pk_collection_warning",
            "fk_collection_warning_attempt",
            "ck_collection_warning_index_non_negative",
            "ck_collection_warning_code_format",
            "ck_collection_warning_message_non_blank");

    private static final List<String> SAMPLE_COLUMNS = List.of(
            "id",
            "appliance_id",
            "collection_attempt_id",
            "metric_name",
            "unit",
            "value",
            "observed_at",
            "ingested_at");

    private static final Set<String> SAMPLE_CONSTRAINTS = Set.of(
            "pk_metric_sample",
            "fk_metric_sample_appliance",
            "fk_metric_sample_attempt_appliance",
            "ck_metric_sample_metric_name_format",
            "ck_metric_sample_unit_format",
            "ck_metric_sample_value_finite");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Flyway flyway;

    @BeforeEach
    @AfterEach
    void removeMetricsAndApplianceRows() {
        jdbcTemplate.update("DELETE FROM metric_sample");
        jdbcTemplate.update("DELETE FROM collection_warning");
        jdbcTemplate.update("DELETE FROM collection_attempt");
        jdbcTemplate.update("DELETE FROM appliance");
    }

    @Test
    void appliesFlywayV1AndV2AndCreatesExactlyTheApprovedApplicationTables() {
        assertThatCode(flyway::validate).doesNotThrowAnyException();

        MigrationInfo[] applied = flyway.info().applied();
        assertThat(applied)
                .extracting(info -> info.getVersion().toString())
                .containsExactly("1", "2");
        assertThat(applied)
                .extracting(MigrationInfo::getDescription)
                .containsExactly("create appliance", "create metric collection schema");
        assertThat(applied).allMatch(info -> info.getState().name().equals("SUCCESS"));
        assertThat(flyway.info().current().getVersion().toString()).isEqualTo("2");
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(flyway.info().all())
                .noneMatch(info -> info.getState().name().contains("FAILED"));

        assertThat(applicationTables())
                .containsExactly(
                        "appliance",
                        "collection_attempt",
                        "collection_warning",
                        "metric_sample");
    }

    @Test
    void definesExactCollectionAttemptColumnsConstraintsAndForeignKeys() {
        List<ColumnMetadata> columns = columns("collection_attempt");
        assertThat(columns).extracting(ColumnMetadata::name).containsExactlyElementsOf(ATTEMPT_COLUMNS);
        assertThat(columns).hasSize(11);

        Map<String, ColumnMetadata> byName = byColumnName(columns);
        assertColumn(byName, "id", "uuid", null, null, null, false);
        assertColumn(byName, "appliance_id", "uuid", null, null, null, false);
        assertColumn(byName, "trigger", "character varying", 16, null, null, false);
        assertColumn(byName, "outcome", "character varying", 20, null, null, false);
        assertColumn(byName, "started_at", "timestamp with time zone", null, null, null, false);
        assertColumn(byName, "completed_at", "timestamp with time zone", null, null, null, false);
        assertColumn(byName, "sample_count", "integer", null, 32, 0, false);
        assertColumn(byName, "failure_category", "character varying", 20, null, null, true);
        assertColumn(byName, "failure_message", "character varying", 500, null, null, true);
        assertColumn(byName, "retry_after_seconds", "integer", null, 32, 0, true);
        assertColumn(
                byName,
                "next_collection_due_at",
                "timestamp with time zone",
                null,
                null,
                null,
                true);

        assertThat(columns).allMatch(column -> column.defaultExpression() == null);
        assertThat(columns)
                .filteredOn(ColumnMetadata::nullable)
                .extracting(ColumnMetadata::name)
                .containsExactly(
                        "failure_category",
                        "failure_message",
                        "retry_after_seconds",
                        "next_collection_due_at");
        assertThat(constraintNames("collection_attempt"))
                .containsExactlyInAnyOrderElementsOf(ATTEMPT_CONSTRAINTS)
                .hasSize(12);

        ForeignKeyMetadata applianceForeignKey = foreignKey(
                "collection_attempt", "fk_collection_attempt_appliance");
        assertForeignKey(
                applianceForeignKey,
                "appliance",
                List.of("appliance_id"),
                List.of("id"));
    }

    @Test
    void acceptsEveryApprovedCollectionAttemptShape() {
        UUID applianceId = insertAppliance();

        insertAttempt(validSuccessAttempt(applianceId));
        insertAttempt(validSuccessAttempt(applianceId)
                .withTrigger("SCHEDULED")
                .withOutcome("PARTIAL_SUCCESS")
                .withSampleCount(2));
        insertAttempt(validFailedAttempt(applianceId, "TIMEOUT")
                .withFailureMessage("Vendor request timed out"));
        insertAttempt(validFailedAttempt(applianceId, "RATE_LIMITED"));
        insertAttempt(validFailedAttempt(applianceId, "RATE_LIMITED")
                .withRetryAfterSeconds(30));
        for (String category : List.of("INVALID_DATA", "TRANSIENT", "UNEXPECTED")) {
            insertAttempt(validFailedAttempt(applianceId, category));
        }
        insertAttempt(validSuccessAttempt(applianceId).withNextCollectionDueAt(null));
        insertAttempt(validSuccessAttempt(applianceId)
                .withCompletedAt(STARTED_AT)
                .withNextCollectionDueAt(STARTED_AT));

        assertThat(attemptCount()).isEqualTo(10);
    }

    @Test
    void rejectsInvalidCollectionAttemptShapes() {
        UUID applianceId = insertAppliance();

        assertSqlState(
                "23503", () -> insertAttempt(validSuccessAttempt(UUID.randomUUID())));
        assertSqlState(
                "23514", () -> insertAttempt(validSuccessAttempt(applianceId).withTrigger("AUTOMATIC")));
        assertSqlState(
                "23514", () -> insertAttempt(validSuccessAttempt(applianceId).withTrigger("manual")));
        assertSqlState(
                "23514", () -> insertAttempt(validSuccessAttempt(applianceId).withOutcome("PARTIAL")));
        assertSqlState(
                "23514", () -> insertAttempt(validSuccessAttempt(applianceId).withOutcome("success")));
        assertSqlState(
                "23514",
                () -> insertAttempt(validSuccessAttempt(applianceId)
                        .withCompletedAt(STARTED_AT.minusNanos(1_000))));
        assertSqlState(
                "23514", () -> insertAttempt(validSuccessAttempt(applianceId).withSampleCount(-1)));
        assertSqlState(
                "23514", () -> insertAttempt(validSuccessAttempt(applianceId).withSampleCount(0)));
        assertSqlState(
                "23514",
                () -> insertAttempt(validSuccessAttempt(applianceId)
                        .withOutcome("PARTIAL_SUCCESS")
                        .withSampleCount(0)));
        assertSqlState(
                "23514",
                () -> insertAttempt(validFailedAttempt(applianceId, "TIMEOUT").withSampleCount(1)));

        assertSqlState(
                "23514",
                () -> insertAttempt(validSuccessAttempt(applianceId).withFailureCategory("TIMEOUT")));
        assertSqlState(
                "23514",
                () -> insertAttempt(validSuccessAttempt(applianceId).withFailureMessage("failure")));
        assertSqlState(
                "23514",
                () -> insertAttempt(validSuccessAttempt(applianceId).withRetryAfterSeconds(30)));
        assertSqlState(
                "23514",
                () -> insertAttempt(validSuccessAttempt(applianceId)
                        .withOutcome("PARTIAL_SUCCESS")
                        .withFailureCategory("TIMEOUT")));
        assertSqlState(
                "23514",
                () -> insertAttempt(validSuccessAttempt(applianceId)
                        .withOutcome("PARTIAL_SUCCESS")
                        .withFailureMessage("failure")));
        assertSqlState(
                "23514",
                () -> insertAttempt(validSuccessAttempt(applianceId)
                        .withOutcome("PARTIAL_SUCCESS")
                        .withRetryAfterSeconds(30)));
        assertSqlState(
                "23514",
                () -> insertAttempt(validFailedAttempt(applianceId, null)));
        assertSqlState(
                "23514",
                () -> insertAttempt(validFailedAttempt(applianceId, "BUSY")));
        assertSqlState(
                "23514",
                () -> insertAttempt(validFailedAttempt(applianceId, "RATE_LIMITED")
                        .withRetryAfterSeconds(0)));
        assertSqlState(
                "23514",
                () -> insertAttempt(validFailedAttempt(applianceId, "RATE_LIMITED")
                        .withRetryAfterSeconds(-1)));
        assertSqlState(
                "23514",
                () -> insertAttempt(validFailedAttempt(applianceId, "TIMEOUT")
                        .withRetryAfterSeconds(30)));
        assertSqlState(
                "23514",
                () -> insertAttempt(validFailedAttempt(applianceId, "TRANSIENT")
                        .withRetryAfterSeconds(30)));
        assertSqlState(
                "23514",
                () -> insertAttempt(validSuccessAttempt(applianceId)
                        .withNextCollectionDueAt(COMPLETED_AT.minusNanos(1_000))));
    }

    @Test
    void definesAndEnforcesTheOrderedCollectionWarningSchema() {
        List<ColumnMetadata> columns = columns("collection_warning");
        assertThat(columns).extracting(ColumnMetadata::name).containsExactlyElementsOf(WARNING_COLUMNS);
        assertThat(columns).hasSize(4).allMatch(column -> !column.nullable());
        assertThat(columns).allMatch(column -> column.defaultExpression() == null);

        Map<String, ColumnMetadata> byName = byColumnName(columns);
        assertColumn(byName, "collection_attempt_id", "uuid", null, null, null, false);
        assertColumn(byName, "warning_index", "integer", null, 32, 0, false);
        assertColumn(byName, "code", "character varying", 64, null, null, false);
        assertColumn(byName, "message", "character varying", 500, null, null, false);
        assertThat(constraintNames("collection_warning"))
                .containsExactlyInAnyOrderElementsOf(WARNING_CONSTRAINTS)
                .hasSize(5);
        assertForeignKey(
                foreignKey("collection_warning", "fk_collection_warning_attempt"),
                "collection_attempt",
                List.of("collection_attempt_id"),
                List.of("id"));
        assertThat(primaryKeyColumns("collection_warning", "pk_collection_warning"))
                .containsExactly("collection_attempt_id", "warning_index");

        UUID applianceId = insertAppliance();
        UUID firstAttemptId = insertAttempt(validSuccessAttempt(applianceId));
        UUID secondAttemptId = insertAttempt(validSuccessAttempt(applianceId));

        insertWarning(firstAttemptId, 1, "MALFORMED_VALUE", "Malformed value omitted");
        insertWarning(firstAttemptId, 0, "UNKNOWN_METRIC", "Unknown metric omitted");
        insertWarning(firstAttemptId, 2, "INCOMPATIBLE_UNIT", "Incompatible unit omitted");
        insertWarning(firstAttemptId, 3, "ANOTHER_VALID_CODE", "Another safe warning");
        insertWarning(secondAttemptId, 0, "UNKNOWN_METRIC", "Independent warning index");

        assertThat(jdbcTemplate.queryForList(
                        """
                        SELECT warning_index
                        FROM collection_warning
                        WHERE collection_attempt_id = ?
                        ORDER BY warning_index ASC
                        """,
                        Integer.class,
                        firstAttemptId))
                .containsExactly(0, 1, 2, 3);

        assertSqlState(
                "23503",
                () -> insertWarning(UUID.randomUUID(), 0, "UNKNOWN_METRIC", "Orphan"));
        assertSqlState(
                "23514",
                () -> insertWarning(firstAttemptId, -1, "UNKNOWN_METRIC", "Negative index"));
        assertSqlState(
                "23505",
                () -> insertWarning(firstAttemptId, 0, "UNKNOWN_METRIC", "Duplicate index"));
        for (String code : List.of("", "unknown_metric", "INVALID-CODE", "INVALID CODE")) {
            assertSqlState(
                    "23514", () -> insertWarning(secondAttemptId, 1, code, "Invalid code"));
        }
        assertSqlState(
                "23514", () -> insertWarning(secondAttemptId, 1, "VALID_CODE", ""));
        assertSqlState(
                "23514", () -> insertWarning(secondAttemptId, 1, "VALID_CODE", "   "));
        assertSqlState(
                "22001", () -> insertWarning(secondAttemptId, 1, "A".repeat(65), "Too long"));
        assertSqlState(
                "22001",
                () -> insertWarning(secondAttemptId, 1, "VALID_CODE", "M".repeat(501)));
    }

    @Test
    void definesAndEnforcesTheMetricSampleSchemaAndFormats() {
        List<ColumnMetadata> columns = columns("metric_sample");
        assertThat(columns).extracting(ColumnMetadata::name).containsExactlyElementsOf(SAMPLE_COLUMNS);
        assertThat(columns).hasSize(8).allMatch(column -> !column.nullable());
        assertThat(columns).allMatch(column -> column.defaultExpression() == null);

        Map<String, ColumnMetadata> byName = byColumnName(columns);
        assertColumn(byName, "id", "uuid", null, null, null, false);
        assertColumn(byName, "appliance_id", "uuid", null, null, null, false);
        assertColumn(byName, "collection_attempt_id", "uuid", null, null, null, false);
        assertColumn(byName, "metric_name", "character varying", 64, null, null, false);
        assertColumn(byName, "unit", "character varying", 32, null, null, false);
        assertColumn(byName, "value", "numeric", null, 20, 6, false);
        assertColumn(byName, "observed_at", "timestamp with time zone", null, null, null, false);
        assertColumn(byName, "ingested_at", "timestamp with time zone", null, null, null, false);
        assertThat(constraintNames("metric_sample"))
                .containsExactlyInAnyOrderElementsOf(SAMPLE_CONSTRAINTS)
                .hasSize(6);

        UUID applianceId = insertAppliance();
        UUID attemptId = insertAttempt(validSuccessAttempt(applianceId).withSampleCount(10));
        insertSample(validSample(applianceId, attemptId));
        insertSample(validSample(applianceId, attemptId)
                .withMetricName("POWER")
                .withUnit("WATT")
                .withValue(new BigDecimal("125.000000")));
        insertSample(validSample(applianceId, attemptId).withValue(BigDecimal.ZERO));
        insertSample(validSample(applianceId, attemptId).withValue(new BigDecimal("-21.500000")));
        insertSample(validSample(applianceId, attemptId)
                .withObservedAt(COMPLETED_AT.plusSeconds(3))
                .withIngestedAt(COMPLETED_AT));

        for (String metricName : List.of("temperature", "TEMP-C", "")) {
            assertSqlState(
                    "23514",
                    () -> insertSample(validSample(applianceId, attemptId)
                            .withMetricName(metricName)));
        }
        for (String unit : List.of("celsius", "DEGREES C", "")) {
            assertSqlState(
                    "23514",
                    () -> insertSample(validSample(applianceId, attemptId).withUnit(unit)));
        }
    }

    @Test
    void enforcesFiniteNumericPrecisionAndUsesPostgresScaleRounding() {
        UUID applianceId = insertAppliance();
        UUID attemptId = insertAttempt(validSuccessAttempt(applianceId).withSampleCount(8));

        UUID ordinaryId = insertSample(validSample(applianceId, attemptId)
                .withValue(new BigDecimal("21.5")));
        UUID roundedId = insertSample(validSample(applianceId, attemptId)
                .withValue(new BigDecimal("1.2345675")));
        insertSample(validSample(applianceId, attemptId).withValue(BigDecimal.ZERO));
        insertSample(validSample(applianceId, attemptId).withValue(new BigDecimal("-1.2345675")));
        insertSample(validSample(applianceId, attemptId)
                .withValue(new BigDecimal("99999999999999.999999")));
        insertSample(validSample(applianceId, attemptId)
                .withValue(new BigDecimal("-99999999999999.999999")));

        assertThat(storedValue(ordinaryId)).isEqualTo(new BigDecimal("21.500000"));
        assertThat(storedValue(ordinaryId).scale()).isEqualTo(6);
        assertThat(storedValue(roundedId)).isEqualTo(new BigDecimal("1.234568"));
        assertSqlState(
                "22003",
                () -> insertSample(validSample(applianceId, attemptId)
                        .withValue(new BigDecimal("100000000000000.000000"))));
        assertSqlState("23514", () -> insertSpecialNumericSample(applianceId, attemptId, "NaN"));
        assertSqlState(
                "22003", () -> insertSpecialNumericSample(applianceId, attemptId, "Infinity"));
        assertSqlState(
                "22003", () -> insertSpecialNumericSample(applianceId, attemptId, "-Infinity"));
    }

    @Test
    void enforcesSameApplianceReferencesAndAllowsDuplicateSampleContent() {
        UUID applianceA = insertAppliance();
        UUID applianceB = insertAppliance();
        UUID attemptA = insertAttempt(validSuccessAttempt(applianceA).withSampleCount(2));

        MetricSampleRow content = validSample(applianceA, attemptA);
        insertSample(content);
        insertSample(content.withId(UUID.randomUUID()));
        assertThat(sampleCount()).isEqualTo(2);

        assertSqlState(
                "23503", () -> insertSample(validSample(applianceB, attemptA)));
        assertSqlState(
                "23503", () -> insertSample(validSample(applianceA, UUID.randomUUID())));
        assertSqlState(
                "23503", () -> insertSample(validSample(UUID.randomUUID(), attemptA)));

        assertForeignKey(
                foreignKey("metric_sample", "fk_metric_sample_appliance"),
                "appliance",
                List.of("appliance_id"),
                List.of("id"));
        assertForeignKey(
                foreignKey("metric_sample", "fk_metric_sample_attempt_appliance"),
                "collection_attempt",
                List.of("collection_attempt_id", "appliance_id"),
                List.of("id", "appliance_id"));
        assertThat(uniqueConstraintColumns(
                        "collection_attempt", "uk_collection_attempt_id_appliance_id"))
                .containsExactly("id", "appliance_id");
        assertThat(constraintNamesByType("metric_sample", "u")).isEmpty();
    }

    @Test
    void restrictsDeletionOfReferencedHistoricalRows() {
        UUID applianceWithAttempt = insertAppliance();
        UUID attemptWithoutChildren = insertAttempt(validSuccessAttempt(applianceWithAttempt));
        assertSqlState(
                "23503", () -> jdbcTemplate.update("DELETE FROM appliance WHERE id = ?", applianceWithAttempt));

        UUID applianceWithWarning = insertAppliance();
        UUID attemptWithWarning = insertAttempt(validSuccessAttempt(applianceWithWarning));
        insertWarning(attemptWithWarning, 0, "UNKNOWN_METRIC", "Safe warning");
        assertSqlState(
                "23503",
                () -> jdbcTemplate.update(
                        "DELETE FROM collection_attempt WHERE id = ?", attemptWithWarning));

        UUID applianceWithSample = insertAppliance();
        UUID attemptWithSample = insertAttempt(validSuccessAttempt(applianceWithSample));
        insertSample(validSample(applianceWithSample, attemptWithSample));
        assertSqlState(
                "23503",
                () -> jdbcTemplate.update(
                        "DELETE FROM collection_attempt WHERE id = ?", attemptWithSample));
        assertSqlState(
                "23503",
                () -> jdbcTemplate.update("DELETE FROM appliance WHERE id = ?", applianceWithSample));

        assertThat(attemptWithoutChildren).isNotNull();
    }

    @Test
    void createsExactlyTheApprovedMetricsIndexesAndDirections() {
        Map<String, IndexMetadata> attemptIndexes = indexes("collection_attempt").stream()
                .collect(Collectors.toMap(IndexMetadata::name, Function.identity()));
        assertThat(attemptIndexes.keySet())
                .containsExactlyInAnyOrder(
                        "pk_collection_attempt",
                        "uk_collection_attempt_id_appliance_id",
                        "idx_collection_attempt_appliance_started_at_id",
                        "idx_collection_attempt_appliance_trigger_started_at_id",
                        "idx_collection_attempt_appliance_outcome_started_at_id");
        assertIndex(
                attemptIndexes,
                "idx_collection_attempt_appliance_started_at_id",
                List.of("appliance_id", "started_at", "id"),
                List.of(),
                List.of(false, true, false),
                false,
                false);
        assertIndex(
                attemptIndexes,
                "idx_collection_attempt_appliance_trigger_started_at_id",
                List.of("appliance_id", "trigger", "started_at", "id"),
                List.of(),
                List.of(false, false, true, false),
                false,
                false);
        assertIndex(
                attemptIndexes,
                "idx_collection_attempt_appliance_outcome_started_at_id",
                List.of("appliance_id", "outcome", "started_at", "id"),
                List.of(),
                List.of(false, false, true, false),
                false,
                false);

        Map<String, IndexMetadata> warningIndexes = indexes("collection_warning").stream()
                .collect(Collectors.toMap(IndexMetadata::name, Function.identity()));
        assertThat(warningIndexes.keySet()).containsExactly("pk_collection_warning");

        Map<String, IndexMetadata> sampleIndexes = indexes("metric_sample").stream()
                .collect(Collectors.toMap(IndexMetadata::name, Function.identity()));
        assertThat(sampleIndexes.keySet())
                .containsExactlyInAnyOrder(
                        "pk_metric_sample",
                        "idx_metric_sample_appliance_observed_at_id",
                        "idx_metric_sample_observed_at_appliance_metric_unit");
        assertIndex(
                sampleIndexes,
                "idx_metric_sample_appliance_observed_at_id",
                List.of("appliance_id", "observed_at", "id"),
                List.of(),
                List.of(false, false, false),
                false,
                false);
        assertIndex(
                sampleIndexes,
                "idx_metric_sample_observed_at_appliance_metric_unit",
                List.of("observed_at", "appliance_id", "metric_name", "unit"),
                List.of("value"),
                List.of(false, false, false, false),
                false,
                false);

        assertThat(explicitMetricsIndexes())
                .containsExactlyInAnyOrder(
                        "idx_collection_attempt_appliance_started_at_id",
                        "idx_collection_attempt_appliance_trigger_started_at_id",
                        "idx_collection_attempt_appliance_outcome_started_at_id",
                        "idx_metric_sample_appliance_observed_at_id",
                        "idx_metric_sample_observed_at_appliance_metric_unit");
    }

    private List<String> applicationTables() {
        return jdbcTemplate.queryForList(
                """
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name <> 'flyway_schema_history'
                ORDER BY table_name
                """,
                String.class);
    }

    private List<ColumnMetadata> columns(String tableName) {
        return jdbcTemplate.query(
                """
                SELECT column_name,
                       data_type,
                       character_maximum_length,
                       numeric_precision,
                       numeric_scale,
                       is_nullable,
                       column_default
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = ?
                ORDER BY ordinal_position
                """,
                (resultSet, rowNumber) -> new ColumnMetadata(
                        resultSet.getString("column_name"),
                        resultSet.getString("data_type"),
                        resultSet.getObject("character_maximum_length", Integer.class),
                        resultSet.getObject("numeric_precision", Integer.class),
                        resultSet.getObject("numeric_scale", Integer.class),
                        resultSet.getString("is_nullable").equals("YES"),
                        resultSet.getString("column_default")),
                tableName);
    }

    private Map<String, ColumnMetadata> byColumnName(List<ColumnMetadata> columns) {
        return columns.stream().collect(Collectors.toMap(ColumnMetadata::name, Function.identity()));
    }

    private List<String> constraintNames(String tableName) {
        return jdbcTemplate.queryForList(
                """
                SELECT constraint_definition.conname
                FROM pg_constraint constraint_definition
                JOIN pg_class constrained_table
                  ON constrained_table.oid = constraint_definition.conrelid
                JOIN pg_namespace constrained_schema
                  ON constrained_schema.oid = constrained_table.relnamespace
                WHERE constrained_schema.nspname = 'public'
                  AND constrained_table.relname = ?
                ORDER BY constraint_definition.conname
                """,
                String.class,
                tableName);
    }

    private List<String> constraintNamesByType(String tableName, String constraintType) {
        return jdbcTemplate.queryForList(
                """
                SELECT constraint_definition.conname
                FROM pg_constraint constraint_definition
                JOIN pg_class constrained_table
                  ON constrained_table.oid = constraint_definition.conrelid
                JOIN pg_namespace constrained_schema
                  ON constrained_schema.oid = constrained_table.relnamespace
                WHERE constrained_schema.nspname = 'public'
                  AND constrained_table.relname = ?
                  AND constraint_definition.contype = ?
                ORDER BY constraint_definition.conname
                """,
                String.class,
                tableName,
                constraintType);
    }

    private ForeignKeyMetadata foreignKey(String tableName, String constraintName) {
        return jdbcTemplate.queryForObject(
                """
                SELECT referenced_table.relname AS referenced_table,
                       constraint_definition.confdeltype::text AS delete_action,
                       array_agg(local_column.attname ORDER BY local_key.ordinality)
                           AS local_columns,
                       array_agg(referenced_column.attname ORDER BY local_key.ordinality)
                           AS referenced_columns
                FROM pg_constraint constraint_definition
                JOIN pg_class constrained_table
                  ON constrained_table.oid = constraint_definition.conrelid
                JOIN pg_namespace constrained_schema
                  ON constrained_schema.oid = constrained_table.relnamespace
                JOIN pg_class referenced_table
                  ON referenced_table.oid = constraint_definition.confrelid
                CROSS JOIN LATERAL unnest(
                    constraint_definition.conkey
                ) WITH ORDINALITY AS local_key(attribute_number, ordinality)
                JOIN LATERAL unnest(
                    constraint_definition.confkey
                ) WITH ORDINALITY AS referenced_key(attribute_number, ordinality)
                  ON referenced_key.ordinality = local_key.ordinality
                JOIN pg_attribute local_column
                  ON local_column.attrelid = constrained_table.oid
                 AND local_column.attnum = local_key.attribute_number
                JOIN pg_attribute referenced_column
                  ON referenced_column.attrelid = referenced_table.oid
                 AND referenced_column.attnum = referenced_key.attribute_number
                WHERE constrained_schema.nspname = 'public'
                  AND constrained_table.relname = ?
                  AND constraint_definition.conname = ?
                  AND constraint_definition.contype = 'f'
                GROUP BY referenced_table.relname, constraint_definition.confdeltype
                """,
                (resultSet, rowNumber) -> new ForeignKeyMetadata(
                        resultSet.getString("referenced_table"),
                        resultSet.getString("delete_action"),
                        sqlArray(resultSet.getArray("local_columns").getArray()),
                        sqlArray(resultSet.getArray("referenced_columns").getArray())),
                tableName,
                constraintName);
    }

    private List<String> primaryKeyColumns(String tableName, String constraintName) {
        return constraintColumns(tableName, constraintName);
    }

    private List<String> uniqueConstraintColumns(String tableName, String constraintName) {
        return constraintColumns(tableName, constraintName);
    }

    private List<String> constraintColumns(String tableName, String constraintName) {
        return jdbcTemplate.queryForList(
                """
                SELECT constrained_column.attname
                FROM pg_constraint constraint_definition
                JOIN pg_class constrained_table
                  ON constrained_table.oid = constraint_definition.conrelid
                JOIN pg_namespace constrained_schema
                  ON constrained_schema.oid = constrained_table.relnamespace
                CROSS JOIN LATERAL unnest(
                    constraint_definition.conkey
                ) WITH ORDINALITY AS constrained_key(attribute_number, ordinality)
                JOIN pg_attribute constrained_column
                  ON constrained_column.attrelid = constrained_table.oid
                 AND constrained_column.attnum = constrained_key.attribute_number
                WHERE constrained_schema.nspname = 'public'
                  AND constrained_table.relname = ?
                  AND constraint_definition.conname = ?
                ORDER BY constrained_key.ordinality
                """,
                String.class,
                tableName,
                constraintName);
    }

    private List<IndexMetadata> indexes(String tableName) {
        return jdbcTemplate.query(
                """
                SELECT index_table.relname AS index_name,
                       index_definition.indisprimary AS primary_index,
                       index_definition.indisunique AS unique_index,
                       index_definition.indpred IS NOT NULL AS partial_index,
                       index_definition.indnkeyatts AS key_count,
                       index_definition.indoption::text AS sort_options,
                       array_agg(
                           table_column.attname
                           ORDER BY indexed_column.ordinality
                       ) AS indexed_columns
                FROM pg_index index_definition
                JOIN pg_class constrained_table
                  ON constrained_table.oid = index_definition.indrelid
                JOIN pg_namespace constrained_schema
                  ON constrained_schema.oid = constrained_table.relnamespace
                JOIN pg_class index_table
                  ON index_table.oid = index_definition.indexrelid
                CROSS JOIN LATERAL unnest(
                    index_definition.indkey::smallint[]
                ) WITH ORDINALITY AS indexed_column(attribute_number, ordinality)
                JOIN pg_attribute table_column
                  ON table_column.attrelid = constrained_table.oid
                 AND table_column.attnum = indexed_column.attribute_number
                WHERE constrained_schema.nspname = 'public'
                  AND constrained_table.relname = ?
                GROUP BY index_table.relname,
                         index_definition.indisprimary,
                         index_definition.indisunique,
                         index_definition.indpred,
                         index_definition.indnkeyatts,
                         index_definition.indoption
                ORDER BY index_table.relname
                """,
                (resultSet, rowNumber) -> {
                    List<String> allColumns =
                            sqlArray(resultSet.getArray("indexed_columns").getArray());
                    int keyCount = resultSet.getInt("key_count");
                    return new IndexMetadata(
                            resultSet.getString("index_name"),
                            resultSet.getBoolean("primary_index"),
                            resultSet.getBoolean("unique_index"),
                            resultSet.getBoolean("partial_index"),
                            allColumns.subList(0, keyCount),
                            allColumns.subList(keyCount, allColumns.size()),
                            parseSortOptions(resultSet.getString("sort_options")));
                },
                tableName);
    }

    private List<String> explicitMetricsIndexes() {
        return jdbcTemplate.queryForList(
                """
                SELECT indexname
                FROM pg_indexes
                WHERE schemaname = 'public'
                  AND (
                      indexname LIKE 'idx_collection_attempt_%'
                      OR indexname LIKE 'idx_collection_warning_%'
                      OR indexname LIKE 'idx_metric_sample_%'
                  )
                ORDER BY indexname
                """,
                String.class);
    }

    private List<String> sqlArray(Object array) {
        return Arrays.stream((Object[]) array).map(Object::toString).toList();
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
            Integer numericPrecision,
            Integer numericScale,
            boolean nullable) {
        ColumnMetadata column = columns.get(name);
        assertThat(column).isNotNull();
        assertThat(column.dataType()).isEqualTo(dataType);
        assertThat(column.maximumLength()).isEqualTo(maximumLength);
        assertThat(column.numericPrecision()).isEqualTo(numericPrecision);
        assertThat(column.numericScale()).isEqualTo(numericScale);
        assertThat(column.nullable()).isEqualTo(nullable);
    }

    private void assertForeignKey(
            ForeignKeyMetadata foreignKey,
            String referencedTable,
            List<String> localColumns,
            List<String> referencedColumns) {
        assertThat(foreignKey.referencedTable()).isEqualTo(referencedTable);
        assertThat(foreignKey.deleteAction()).isEqualTo("r");
        assertThat(foreignKey.localColumns()).containsExactlyElementsOf(localColumns);
        assertThat(foreignKey.referencedColumns()).containsExactlyElementsOf(referencedColumns);
    }

    private void assertIndex(
            Map<String, IndexMetadata> indexes,
            String name,
            List<String> keyColumns,
            List<String> includedColumns,
            List<Boolean> descending,
            boolean unique,
            boolean partial) {
        IndexMetadata index = indexes.get(name);
        assertThat(index).isNotNull();
        assertThat(index.keyColumns()).containsExactlyElementsOf(keyColumns);
        assertThat(index.includedColumns()).containsExactlyElementsOf(includedColumns);
        assertThat(index.unique()).isEqualTo(unique);
        assertThat(index.partial()).isEqualTo(partial);
        assertThat(index.primary()).isFalse();
        assertThat(index.sortOptions())
                .hasSize(keyColumns.size())
                .extracting(option -> (option & 1) == 1)
                .containsExactlyElementsOf(descending);
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

    private UUID insertAppliance() {
        UUID id = UUID.randomUUID();
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
                "Metrics schema appliance",
                "mock-alpha",
                "metrics-schema-" + UUID.randomUUID(),
                30,
                STARTED_AT,
                STARTED_AT,
                STARTED_AT);
        return id;
    }

    private UUID insertAttempt(CollectionAttemptRow row) {
        jdbcTemplate.update(
                """
                INSERT INTO collection_attempt (
                    id,
                    appliance_id,
                    trigger,
                    outcome,
                    started_at,
                    completed_at,
                    sample_count,
                    failure_category,
                    failure_message,
                    retry_after_seconds,
                    next_collection_due_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                row.id(),
                row.applianceId(),
                row.trigger(),
                row.outcome(),
                row.startedAt(),
                row.completedAt(),
                row.sampleCount(),
                row.failureCategory(),
                row.failureMessage(),
                row.retryAfterSeconds(),
                row.nextCollectionDueAt());
        return row.id();
    }

    private void insertWarning(UUID attemptId, int index, String code, String message) {
        jdbcTemplate.update(
                """
                INSERT INTO collection_warning (
                    collection_attempt_id,
                    warning_index,
                    code,
                    message
                ) VALUES (?, ?, ?, ?)
                """,
                attemptId,
                index,
                code,
                message);
    }

    private UUID insertSample(MetricSampleRow row) {
        jdbcTemplate.update(
                """
                INSERT INTO metric_sample (
                    id,
                    appliance_id,
                    collection_attempt_id,
                    metric_name,
                    unit,
                    value,
                    observed_at,
                    ingested_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                row.id(),
                row.applianceId(),
                row.collectionAttemptId(),
                row.metricName(),
                row.unit(),
                row.value(),
                row.observedAt(),
                row.ingestedAt());
        return row.id();
    }

    private void insertSpecialNumericSample(UUID applianceId, UUID attemptId, String value) {
        jdbcTemplate.update(
                """
                INSERT INTO metric_sample (
                    id,
                    appliance_id,
                    collection_attempt_id,
                    metric_name,
                    unit,
                    value,
                    observed_at,
                    ingested_at
                ) VALUES (?, ?, ?, ?, ?, CAST(? AS numeric), ?, ?)
                """,
                UUID.randomUUID(),
                applianceId,
                attemptId,
                "TEMPERATURE",
                "CELSIUS",
                value,
                COMPLETED_AT,
                COMPLETED_AT);
    }

    private CollectionAttemptRow validSuccessAttempt(UUID applianceId) {
        return new CollectionAttemptRow(
                UUID.randomUUID(),
                applianceId,
                "MANUAL",
                "SUCCESS",
                STARTED_AT,
                COMPLETED_AT,
                1,
                null,
                null,
                null,
                DUE_AT);
    }

    private CollectionAttemptRow validFailedAttempt(UUID applianceId, String category) {
        return validSuccessAttempt(applianceId)
                .withOutcome("FAILED")
                .withSampleCount(0)
                .withFailureCategory(category);
    }

    private MetricSampleRow validSample(UUID applianceId, UUID attemptId) {
        return new MetricSampleRow(
                UUID.randomUUID(),
                applianceId,
                attemptId,
                "TEMPERATURE",
                "CELSIUS",
                new BigDecimal("21.500000"),
                COMPLETED_AT,
                COMPLETED_AT);
    }

    private BigDecimal storedValue(UUID id) {
        return jdbcTemplate.queryForObject(
                "SELECT value FROM metric_sample WHERE id = ?", BigDecimal.class, id);
    }

    private long attemptCount() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM collection_attempt", Long.class);
        return count == null ? 0 : count;
    }

    private long sampleCount() {
        Long count = jdbcTemplate.queryForObject("SELECT count(*) FROM metric_sample", Long.class);
        return count == null ? 0 : count;
    }

    private record ColumnMetadata(
            String name,
            String dataType,
            Integer maximumLength,
            Integer numericPrecision,
            Integer numericScale,
            boolean nullable,
            String defaultExpression) {}

    private record ForeignKeyMetadata(
            String referencedTable,
            String deleteAction,
            List<String> localColumns,
            List<String> referencedColumns) {}

    private record IndexMetadata(
            String name,
            boolean primary,
            boolean unique,
            boolean partial,
            List<String> keyColumns,
            List<String> includedColumns,
            List<Integer> sortOptions) {}

    private record CollectionAttemptRow(
            UUID id,
            UUID applianceId,
            String trigger,
            String outcome,
            OffsetDateTime startedAt,
            OffsetDateTime completedAt,
            Integer sampleCount,
            String failureCategory,
            String failureMessage,
            Integer retryAfterSeconds,
            OffsetDateTime nextCollectionDueAt) {

        CollectionAttemptRow withTrigger(String value) {
            return copy(value, outcome, completedAt, sampleCount, failureCategory, failureMessage,
                    retryAfterSeconds, nextCollectionDueAt);
        }

        CollectionAttemptRow withOutcome(String value) {
            return copy(trigger, value, completedAt, sampleCount, failureCategory, failureMessage,
                    retryAfterSeconds, nextCollectionDueAt);
        }

        CollectionAttemptRow withCompletedAt(OffsetDateTime value) {
            return copy(trigger, outcome, value, sampleCount, failureCategory, failureMessage,
                    retryAfterSeconds, nextCollectionDueAt);
        }

        CollectionAttemptRow withSampleCount(Integer value) {
            return copy(trigger, outcome, completedAt, value, failureCategory, failureMessage,
                    retryAfterSeconds, nextCollectionDueAt);
        }

        CollectionAttemptRow withFailureCategory(String value) {
            return copy(trigger, outcome, completedAt, sampleCount, value, failureMessage,
                    retryAfterSeconds, nextCollectionDueAt);
        }

        CollectionAttemptRow withFailureMessage(String value) {
            return copy(trigger, outcome, completedAt, sampleCount, failureCategory, value,
                    retryAfterSeconds, nextCollectionDueAt);
        }

        CollectionAttemptRow withRetryAfterSeconds(Integer value) {
            return copy(trigger, outcome, completedAt, sampleCount, failureCategory, failureMessage,
                    value, nextCollectionDueAt);
        }

        CollectionAttemptRow withNextCollectionDueAt(OffsetDateTime value) {
            return copy(trigger, outcome, completedAt, sampleCount, failureCategory, failureMessage,
                    retryAfterSeconds, value);
        }

        private CollectionAttemptRow copy(
                String newTrigger,
                String newOutcome,
                OffsetDateTime newCompletedAt,
                Integer newSampleCount,
                String newFailureCategory,
                String newFailureMessage,
                Integer newRetryAfterSeconds,
                OffsetDateTime newNextCollectionDueAt) {
            return new CollectionAttemptRow(
                    UUID.randomUUID(),
                    applianceId,
                    newTrigger,
                    newOutcome,
                    startedAt,
                    newCompletedAt,
                    newSampleCount,
                    newFailureCategory,
                    newFailureMessage,
                    newRetryAfterSeconds,
                    newNextCollectionDueAt);
        }
    }

    private record MetricSampleRow(
            UUID id,
            UUID applianceId,
            UUID collectionAttemptId,
            String metricName,
            String unit,
            BigDecimal value,
            OffsetDateTime observedAt,
            OffsetDateTime ingestedAt) {

        MetricSampleRow withId(UUID newId) {
            return new MetricSampleRow(
                    newId,
                    applianceId,
                    collectionAttemptId,
                    metricName,
                    unit,
                    value,
                    observedAt,
                    ingestedAt);
        }

        MetricSampleRow withMetricName(String newMetricName) {
            return new MetricSampleRow(
                    UUID.randomUUID(),
                    applianceId,
                    collectionAttemptId,
                    newMetricName,
                    unit,
                    value,
                    observedAt,
                    ingestedAt);
        }

        MetricSampleRow withUnit(String newUnit) {
            return new MetricSampleRow(
                    UUID.randomUUID(),
                    applianceId,
                    collectionAttemptId,
                    metricName,
                    newUnit,
                    value,
                    observedAt,
                    ingestedAt);
        }

        MetricSampleRow withValue(BigDecimal newValue) {
            return new MetricSampleRow(
                    UUID.randomUUID(),
                    applianceId,
                    collectionAttemptId,
                    metricName,
                    unit,
                    newValue,
                    observedAt,
                    ingestedAt);
        }

        MetricSampleRow withObservedAt(OffsetDateTime newObservedAt) {
            return new MetricSampleRow(
                    UUID.randomUUID(),
                    applianceId,
                    collectionAttemptId,
                    metricName,
                    unit,
                    value,
                    newObservedAt,
                    ingestedAt);
        }

        MetricSampleRow withIngestedAt(OffsetDateTime newIngestedAt) {
            return new MetricSampleRow(
                    id,
                    applianceId,
                    collectionAttemptId,
                    metricName,
                    unit,
                    value,
                    observedAt,
                    newIngestedAt);
        }
    }
}
