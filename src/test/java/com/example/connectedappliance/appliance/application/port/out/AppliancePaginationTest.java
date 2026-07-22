package com.example.connectedappliance.appliance.application.port.out;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.example.connectedappliance.appliance.domain.Appliance;
import com.example.connectedappliance.appliance.domain.CollectionState;
import com.example.connectedappliance.appliance.domain.LastCollectionStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppliancePaginationTest {

    @Test
    void validatesZeroBasedPageAndPositiveSizeWithoutApiMaximum() {
        assertThat(new AppliancePageRequest(0, 1)).isEqualTo(new AppliancePageRequest(0, 1));
        assertThat(new AppliancePageRequest(2, 101).size()).isEqualTo(101);
        assertThatThrownBy(() -> new AppliancePageRequest(-1, 20))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AppliancePageRequest(0, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void defensivelyCopiesDomainItemsAndExposesPersistenceNeutralMetadata() {
        List<Appliance> source = new ArrayList<>();
        source.add(appliance());

        AppliancePage page = new AppliancePage(source, 0, 20, 1, 1);
        source.clear();

        assertThat(page.items()).hasSize(1);
        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(20);
        assertThat(page.totalElements()).isOne();
        assertThat(page.totalPages()).isOne();
        assertThatThrownBy(() -> page.items().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsInvalidPageResultMetadataAndNullItems() {
        assertThatThrownBy(() -> new AppliancePage(null, 0, 20, 0, 0))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AppliancePage(List.of(), -1, 20, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AppliancePage(List.of(), 0, 0, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AppliancePage(List.of(), 0, 20, -1, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AppliancePage(List.of(), 0, 20, 0, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Appliance appliance() {
        Instant createdAt = Instant.parse("2026-07-21T10:00:00Z");
        return new Appliance(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "Pagination appliance",
                null,
                "mock-alpha",
                "pagination-reference",
                CollectionState.ACTIVE,
                30,
                createdAt.plusSeconds(30),
                0,
                LastCollectionStatus.NEVER_ATTEMPTED,
                0,
                createdAt,
                createdAt);
    }
}
