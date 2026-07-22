package com.example.connectedappliance.metrics.infrastructure.persistence;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.Repository;

import static org.assertj.core.api.Assertions.assertThat;

class MetricsPersistenceBoundaryTest {

    @Test
    void keepsEntitiesKeysAndRepositoriesPackagePrivate() {
        for (Class<?> type : List.of(
                CollectionAttemptEntity.class,
                CollectionWarningEntity.class,
                CollectionWarningId.class,
                MetricSampleEntity.class,
                SpringDataCollectionAttemptRepository.class,
                SpringDataCollectionWarningRepository.class,
                SpringDataMetricSampleRepository.class,
                MetricsPersistenceAdapter.class,
                MetricsPersistenceMapper.class)) {
            assertThat(Modifier.isPublic(type.getModifiers())).as(type.getSimpleName()).isFalse();
        }
        assertThat(Repository.class.isAssignableFrom(SpringDataCollectionAttemptRepository.class))
                .isTrue();
    }

    @Test
    void mapsExactTablesAndColumnsWithoutGeneratedIdsVersionsOrRelationships() {
        assertThat(CollectionAttemptEntity.class.getAnnotation(Table.class).name())
                .isEqualTo("collection_attempt");
        assertThat(CollectionWarningEntity.class.getAnnotation(Table.class).name())
                .isEqualTo("collection_warning");
        assertThat(MetricSampleEntity.class.getAnnotation(Table.class).name())
                .isEqualTo("metric_sample");

        assertColumns(
                CollectionAttemptEntity.class,
                Set.of(
                        "id", "appliance_id", "trigger", "outcome", "started_at", "completed_at",
                        "sample_count", "failure_category", "failure_message", "retry_after_seconds",
                        "next_collection_due_at"));
        assertColumns(CollectionWarningId.class, Set.of("collection_attempt_id", "warning_index"));
        assertColumns(CollectionWarningEntity.class, Set.of("code", "message"));
        assertColumns(
                MetricSampleEntity.class,
                Set.of(
                        "id", "appliance_id", "collection_attempt_id", "metric_name", "unit",
                        "value", "observed_at", "ingested_at"));

        for (Class<?> entity : List.of(
                CollectionAttemptEntity.class, CollectionWarningEntity.class, MetricSampleEntity.class)) {
            assertThat(fields(entity))
                    .noneMatch(field -> field.isAnnotationPresent(GeneratedValue.class))
                    .noneMatch(field -> field.isAnnotationPresent(Version.class))
                    .noneMatch(field -> field.isAnnotationPresent(ManyToOne.class));
        }
    }

    @Test
    void marksMappedColumnsNonUpdateableAndExposesNoPublicMutationMethods() {
        for (Class<?> type : List.of(
                CollectionAttemptEntity.class,
                CollectionWarningId.class,
                CollectionWarningEntity.class,
                MetricSampleEntity.class)) {
            for (Field field : fields(type)) {
                Column column = field.getAnnotation(Column.class);
                if (column != null) {
                    assertThat(column.updatable()).as(type.getSimpleName() + "." + field.getName())
                            .isFalse();
                }
            }
            assertThat(List.of(type.getDeclaredMethods()))
                    .noneMatch(MetricsPersistenceBoundaryTest::isPublicMutationMethod);
        }
    }

    private static void assertColumns(Class<?> type, Set<String> expected) {
        Set<String> columns = fields(type).stream()
                .map(field -> field.getAnnotation(Column.class))
                .filter(java.util.Objects::nonNull)
                .map(Column::name)
                .collect(Collectors.toSet());
        assertThat(columns).containsExactlyInAnyOrderElementsOf(expected);
    }

    private static List<Field> fields(Class<?> type) {
        return List.of(type.getDeclaredFields());
    }

    private static boolean isPublicMutationMethod(Method method) {
        String name = method.getName();
        return Modifier.isPublic(method.getModifiers())
                && (name.startsWith("set")
                        || name.startsWith("update")
                        || name.startsWith("delete")
                        || name.startsWith("replace"));
    }
}
