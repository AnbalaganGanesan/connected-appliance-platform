package com.example.connectedappliance.appliance.application;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.connectedappliance.appliance.application.port.out.AppliancePage;
import com.example.connectedappliance.appliance.application.port.out.AppliancePageRequest;
import com.example.connectedappliance.appliance.application.port.out.ApplianceRepository;
import com.example.connectedappliance.appliance.domain.CollectionState;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplianceListingServiceTest {

    @Mock
    private ApplianceRepository applianceRepository;

    @Test
    void delegatesDefaultUnfilteredPageAndReturnsExactRepositoryResult() {
        AppliancePage expected = new AppliancePage(List.of(), 0, 20, 0, 0);
        when(applianceRepository.findAll(
                        new AppliancePageRequest(0, 20), Optional.empty()))
                .thenReturn(expected);
        ApplianceListingService service = new ApplianceListingService(applianceRepository);

        AppliancePage actual = service.list(0, 20, Optional.empty());

        assertThat(actual).isSameAs(expected);
        verify(applianceRepository).findAll(
                new AppliancePageRequest(0, 20), Optional.empty());
        verifyNoMoreInteractions(applianceRepository);
    }

    @Test
    void delegatesExplicitPageAndSizeWithoutApplyingAnApiMaximumInTheService() {
        AppliancePage expected = new AppliancePage(List.of(), 3, 100, 0, 0);
        when(applianceRepository.findAll(
                        new AppliancePageRequest(3, 100), Optional.empty()))
                .thenReturn(expected);
        ApplianceListingService service = new ApplianceListingService(applianceRepository);

        AppliancePage actual = service.list(3, 100, Optional.empty());

        assertThat(actual).isSameAs(expected);
        verify(applianceRepository).findAll(
                new AppliancePageRequest(3, 100), Optional.empty());
        verifyNoMoreInteractions(applianceRepository);
    }

    @ParameterizedTest
    @EnumSource(CollectionState.class)
    void delegatesEachCollectionStateFilterUnchanged(CollectionState state) {
        AppliancePage expected = new AppliancePage(List.of(), 1, 10, 0, 0);
        Optional<CollectionState> filter = Optional.of(state);
        when(applianceRepository.findAll(new AppliancePageRequest(1, 10), filter))
                .thenReturn(expected);
        ApplianceListingService service = new ApplianceListingService(applianceRepository);

        AppliancePage actual = service.list(1, 10, filter);

        assertThat(actual).isSameAs(expected);
        verify(applianceRepository).findAll(new AppliancePageRequest(1, 10), filter);
        verifyNoMoreInteractions(applianceRepository);
    }
}
