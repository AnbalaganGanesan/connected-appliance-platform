package com.example.connectedappliance.appliance.infrastructure.persistence;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import com.example.connectedappliance.appliance.domain.CollectionState;
import com.example.connectedappliance.appliance.domain.LastCollectionStatus;
import com.example.connectedappliance.appliance.application.port.in.ApplianceCollectionCommandPort;
import com.example.connectedappliance.appliance.application.port.in.ApplianceCollectionQueryPort;

import static org.assertj.core.api.Assertions.assertThat;

class ApplianceMetadataLockingTest {

    @Test
    void metadataLookupUsesExplicitPessimisticWriteLock() throws Exception {
        Method method = SpringDataApplianceRepository.class
                .getDeclaredMethod("findByIdForUpdate", UUID.class);

        assertThat(method.getAnnotation(Lock.class).value())
                .isEqualTo(LockModeType.PESSIMISTIC_WRITE);
        assertThat(method.getAnnotation(Query.class).value())
                .contains("ApplianceEntity", "appliance.id = :id");
    }

    @Test
    void entityMetadataMutationRemainsPackageInternal() throws Exception {
        Method method = ApplianceEntity.class.getDeclaredMethod(
                "replaceMetadata", String.class, String.class, java.time.Instant.class);

        assertThat(method.getModifiers()).isZero();
    }

    @Test
    void collectionConfigurationUsesTheSameLockedTransactionalPath() throws Exception {
        Method intervalMethod = AppliancePersistenceAdapter.class.getDeclaredMethod(
                "replaceCollectionInterval", UUID.class, int.class, Instant.class);
        Method stateMethod = AppliancePersistenceAdapter.class.getDeclaredMethod(
                "replaceCollectionState", UUID.class, CollectionState.class, Instant.class);

        assertThat(intervalMethod.getAnnotation(Transactional.class)).isNotNull();
        assertThat(stateMethod.getAnnotation(Transactional.class)).isNotNull();
    }

    @Test
    void entityCollectionConfigurationMutationsRemainPackageInternal() throws Exception {
        Method intervalMethod = ApplianceEntity.class.getDeclaredMethod(
                "replaceCollectionInterval", int.class, Instant.class, Instant.class);
        Method stateMethod = ApplianceEntity.class.getDeclaredMethod(
                "replaceCollectionState", CollectionState.class, Instant.class, Instant.class);

        assertThat(intervalMethod.getModifiers()).isZero();
        assertThat(stateMethod.getModifiers()).isZero();
    }

    @Test
    void collectionPortsAreApplianceOwnedAndFinalizationJoinsOuterTransaction()
            throws Exception {
        assertThat(ApplianceCollectionQueryPort.class.isAssignableFrom(
                        AppliancePersistenceAdapter.class))
                .isTrue();
        assertThat(ApplianceCollectionCommandPort.class.isAssignableFrom(
                        AppliancePersistenceAdapter.class))
                .isTrue();

        Method query = AppliancePersistenceAdapter.class.getDeclaredMethod(
                "findCollectionTarget", UUID.class);
        Method lock = AppliancePersistenceAdapter.class.getDeclaredMethod(
                "lockForCollectionFinalization", UUID.class);
        Method apply = AppliancePersistenceAdapter.class.getDeclaredMethod(
                "applyCollectionFinalization",
                com.example.connectedappliance.appliance.application.port.in
                        .ApplianceCollectionFinalizationCommand.class);

        assertThat(query.getAnnotation(Transactional.class).readOnly()).isTrue();
        assertThat(lock.getAnnotation(Transactional.class)).isNull();
        assertThat(apply.getAnnotation(Transactional.class)).isNull();
    }

    @Test
    void entityFinalizationIsPackageInternalAndChangesOnlyApprovedFields() throws Exception {
        Method method = ApplianceEntity.class.getDeclaredMethod(
                "finalizeCollection",
                int.class,
                LastCollectionStatus.class,
                Instant.class,
                Instant.class);
        assertThat(method.getModifiers()).isZero();

        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-07-22T10:00:00Z");
        Instant oldDue = createdAt.plusSeconds(30);
        ApplianceEntity entity = new ApplianceEntity(
                id,
                "Kitchen",
                "Description",
                "mock-alpha",
                "Opaque-1",
                CollectionState.ACTIVE,
                30,
                oldDue,
                2,
                LastCollectionStatus.FAILED,
                7,
                createdAt,
                createdAt);
        Instant completedAt = createdAt.plusSeconds(10);
        Instant newDue = completedAt.plusSeconds(60);

        entity.finalizeCollection(0, LastCollectionStatus.SUCCESS, newDue, completedAt);

        assertThat(entity.id()).isEqualTo(id);
        assertThat(entity.displayName()).isEqualTo("Kitchen");
        assertThat(entity.description()).isEqualTo("Description");
        assertThat(entity.vendorKey()).isEqualTo("mock-alpha");
        assertThat(entity.externalReference()).isEqualTo("Opaque-1");
        assertThat(entity.collectionState()).isEqualTo(CollectionState.ACTIVE);
        assertThat(entity.collectionIntervalSeconds()).isEqualTo(30);
        assertThat(entity.createdAt()).isEqualTo(createdAt);
        assertThat(entity.version()).isEqualTo(7);
        assertThat(entity.consecutiveFailureCount()).isZero();
        assertThat(entity.lastCollectionStatus()).isEqualTo(LastCollectionStatus.SUCCESS);
        assertThat(entity.nextCollectionDueAt()).isEqualTo(newDue);
        assertThat(entity.updatedAt()).isEqualTo(completedAt);
    }
}
