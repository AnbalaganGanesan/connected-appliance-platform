package com.example.connectedappliance.vendor;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.example.connectedappliance.appliance.application.port.out.SupportedVendorPort;
import com.example.connectedappliance.bootstrap.DatabaseIndependentTestSupport;
import com.example.connectedappliance.metrics.application.port.out.VendorMetricBatch;
import com.example.connectedappliance.metrics.application.port.out.VendorMetricRequest;
import com.example.connectedappliance.metrics.application.port.out.VendorMetricSourcePort;
import com.example.connectedappliance.shared.metric.CanonicalMetric;
import com.example.connectedappliance.shared.metric.CanonicalMetricReading;
import com.example.connectedappliance.shared.metric.CanonicalUnit;
import com.example.connectedappliance.vendor.application.VendorAdapterRegistry;
import com.example.connectedappliance.vendor.infrastructure.mockalpha.MockAlphaVendorAdapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = DatabaseIndependentTestSupport.AUTO_CONFIGURATION_EXCLUSIONS)
class VendorModuleWiringTest {

    @Autowired
    private SupportedVendorPort supportedVendorPort;

    @Autowired
    private VendorMetricSourcePort vendorMetricSourcePort;

    @Autowired
    private VendorAdapterRegistry registry;

    @Autowired
    private MockAlphaVendorAdapter mockAlphaVendorAdapter;

    @Test
    void wiresMockAlphaThroughBothConsumerOwnedPorts() {
        assertNotNull(mockAlphaVendorAdapter);
        assertSame(registry, supportedVendorPort);
        assertSame(registry, vendorMetricSourcePort);
        assertTrue(supportedVendorPort.isSupported("mock-alpha"));

        VendorMetricBatch batch = vendorMetricSourcePort.collect(
                new VendorMetricRequest("mock-alpha", "wiring-device"));

        assertEquals(
                List.of(
                        new CanonicalMetricReading(
                                CanonicalMetric.TEMPERATURE,
                                CanonicalUnit.CELSIUS,
                                new BigDecimal("21.500000")),
                        new CanonicalMetricReading(
                                CanonicalMetric.POWER,
                                CanonicalUnit.WATT,
                                new BigDecimal("125.000000"))),
                batch.readings());
    }
}
