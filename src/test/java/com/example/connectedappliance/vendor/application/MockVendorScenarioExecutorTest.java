package com.example.connectedappliance.vendor.application;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.connectedappliance.metrics.application.port.out.VendorFailureCategory;
import com.example.connectedappliance.metrics.application.port.out.VendorMetricBatch;
import com.example.connectedappliance.metrics.application.port.out.VendorMetricException;
import com.example.connectedappliance.metrics.application.port.out.VendorMetricWarning;
import com.example.connectedappliance.metrics.application.port.out.VendorMetricWarningCode;
import com.example.connectedappliance.vendor.application.port.VendorDelay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MockVendorScenarioExecutorTest {

    @Test
    void appliesConfiguredDelayExactlyOnceBeforeProducingBatch() {
        List<String> events = new ArrayList<>();
        VendorDelay delay = configuredDelay -> events.add("delay:" + configuredDelay);
        MockVendorScenarioExecutor executor = new MockVendorScenarioExecutor(delay);

        executor.execute(
                VendorScenario.SUCCESS,
                Duration.ofMillis(125),
                () -> {
                    events.add("success");
                    return new VendorMetricBatch(List.of());
                },
                () -> new VendorMetricBatch(List.of()),
                () -> new VendorMetricBatch(List.of()));

        assertEquals(List.of("delay:PT0.125S", "success"), events);
    }

    @Test
    void configuredFailureAppliesDelayWithoutInvokingBatchSuppliers() {
        List<String> events = new ArrayList<>();
        MockVendorScenarioExecutor executor =
                new MockVendorScenarioExecutor(delay -> events.add("delay"));

        VendorMetricException failure = assertThrows(
                VendorMetricException.class,
                () -> executor.execute(
                        VendorScenario.TIMEOUT,
                        Duration.ZERO,
                        () -> {
                            events.add("success");
                            return new VendorMetricBatch(List.of());
                        },
                        () -> {
                            events.add("partial");
                            return new VendorMetricBatch(List.of());
                        },
                        () -> {
                            events.add("invalid");
                            return new VendorMetricBatch(List.of());
                        }));

        assertEquals(VendorFailureCategory.TIMEOUT, failure.category());
        assertEquals(List.of("delay"), events);
    }

    @Test
    void invalidDataUsesOrderedWarningsFromNoReadingBatch() {
        MockVendorScenarioExecutor executor = new MockVendorScenarioExecutor(delay -> {});
        List<VendorMetricWarning> warnings = List.of(
                VendorMetricWarning.forCode(VendorMetricWarningCode.UNKNOWN_METRIC),
                VendorMetricWarning.forCode(VendorMetricWarningCode.MALFORMED_VALUE));

        VendorMetricException failure = assertThrows(
                VendorMetricException.class,
                () -> executor.execute(
                        VendorScenario.INVALID_DATA,
                        Duration.ZERO,
                        () -> new VendorMetricBatch(List.of()),
                        () -> new VendorMetricBatch(List.of()),
                        () -> new VendorMetricBatch(List.of(), warnings)));

        assertEquals(VendorFailureCategory.INVALID_DATA, failure.category());
        assertEquals(warnings, failure.warnings());
    }
}
