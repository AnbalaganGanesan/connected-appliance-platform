package com.example.connectedappliance.appliance.infrastructure.persistence;

import java.lang.reflect.Method;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

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
}
