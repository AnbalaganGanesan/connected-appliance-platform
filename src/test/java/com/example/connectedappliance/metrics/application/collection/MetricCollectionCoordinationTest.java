package com.example.connectedappliance.metrics.application.collection;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;

import com.example.connectedappliance.appliance.application.port.in.ApplianceCollectionQueryPort;
import com.example.connectedappliance.appliance.application.port.in.ApplianceCollectionTarget;
import com.example.connectedappliance.appliance.domain.CollectionState;
import com.example.connectedappliance.metrics.application.control.GuardedVendorExecution;
import com.example.connectedappliance.metrics.application.control.InMemoryApplianceCollectionGuard;
import com.example.connectedappliance.metrics.application.control.VendorFailureClassifier;
import com.example.connectedappliance.metrics.application.control.VendorTaskExecutor;
import com.example.connectedappliance.metrics.application.port.out.MetricsIdentifierGenerator;
import com.example.connectedappliance.metrics.application.port.out.VendorMetricSourcePort;
import com.example.connectedappliance.metrics.domain.CollectionTrigger;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MetricCollectionCoordinationTest {

    private static final UUID APPLIANCE_ID = new UUID(0, 18);

    @Test
    void actualBusyGuardProducesNoVendorFinalizationOrIdentifiers() {
        InMemoryApplianceCollectionGuard guard = new InMemoryApplianceCollectionGuard();
        var heldPermit = guard.tryAcquire(APPLIANCE_ID).orElseThrow();
        VendorTaskExecutor executor = new VendorTaskExecutor() {
            @Override
            public <T> Future<T> submit(Callable<T> task) {
                throw new AssertionError("busy execution must not submit vendor work");
            }
        };
        Fixtures fixtures = fixtures(guard, executor);

        try (heldPermit) {
            assertThat(fixtures.service.collect(command()))
                    .isInstanceOf(CollectionWorkflowResult.Busy.class);
        }

        verifyNoInteractions(fixtures.vendorPort, fixtures.finalizer, fixtures.identifiers);
        assertThat(guard.tryAcquire(APPLIANCE_ID)).isPresent();
    }

    @Test
    void actualExecutorRejectionProducesSaturatedAndReleasesGuardWithoutSideEffects() {
        InMemoryApplianceCollectionGuard guard = new InMemoryApplianceCollectionGuard();
        VendorTaskExecutor rejectingExecutor = new VendorTaskExecutor() {
            @Override
            public <T> Future<T> submit(Callable<T> task) {
                throw new RejectedExecutionException("controlled saturation");
            }
        };
        Fixtures fixtures = fixtures(guard, rejectingExecutor);

        assertThat(fixtures.service.collect(command()))
                .isInstanceOf(CollectionWorkflowResult.Saturated.class);

        verifyNoInteractions(fixtures.vendorPort, fixtures.finalizer, fixtures.identifiers);
        var nextPermit = guard.tryAcquire(APPLIANCE_ID);
        assertThat(nextPermit).isPresent();
        nextPermit.orElseThrow().close();
    }

    private Fixtures fixtures(
            InMemoryApplianceCollectionGuard guard, VendorTaskExecutor executor) {
        ApplianceCollectionQueryPort queryPort = mock(ApplianceCollectionQueryPort.class);
        when(queryPort.findCollectionTarget(APPLIANCE_ID)).thenReturn(Optional.of(
                new ApplianceCollectionTarget(
                        APPLIANCE_ID,
                        CollectionState.ACTIVE,
                        "mock-alpha",
                        "Opaque-18")));
        VendorMetricSourcePort vendorPort = mock(VendorMetricSourcePort.class);
        CollectionFinalizationService finalizer = mock(CollectionFinalizationService.class);
        MetricsIdentifierGenerator identifiers = mock(MetricsIdentifierGenerator.class);
        GuardedVendorExecution execution = new GuardedVendorExecution(
                guard, executor, new VendorFailureClassifier(), Duration.ofSeconds(1));
        MetricCollectionService service = new MetricCollectionService(
                queryPort,
                vendorPort,
                execution,
                finalizer,
                Clock.fixed(Instant.parse("2026-07-22T10:00:00Z"), ZoneOffset.UTC),
                identifiers);
        return new Fixtures(service, vendorPort, finalizer, identifiers);
    }

    private CollectApplianceCommand command() {
        return new CollectApplianceCommand(APPLIANCE_ID, CollectionTrigger.MANUAL);
    }

    private record Fixtures(
            MetricCollectionService service,
            VendorMetricSourcePort vendorPort,
            CollectionFinalizationService finalizer,
            MetricsIdentifierGenerator identifiers) {}
}
