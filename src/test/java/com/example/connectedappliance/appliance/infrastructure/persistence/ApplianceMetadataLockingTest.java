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
}
