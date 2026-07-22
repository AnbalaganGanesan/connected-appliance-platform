package com.example.connectedappliance.vendor.application;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.example.connectedappliance.shared.metric.CanonicalMetricReading;
import com.example.connectedappliance.vendor.application.port.VendorAdapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class VendorAdapterRegistryStartupTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean("firstAdapter", VendorAdapter.class, () -> new StubAdapter("duplicate"))
            .withBean("secondAdapter", VendorAdapter.class, () -> new StubAdapter("duplicate"))
            .withBean(VendorAdapterRegistry.class);

    @Test
    void duplicateAdapterKeysFailSpringContextStartup() {
        contextRunner.run(context -> {
            Throwable startupFailure = context.getStartupFailure();
            assertNotNull(startupFailure);

            Throwable rootCause = rootCause(startupFailure);
            IllegalStateException duplicateFailure =
                    assertInstanceOf(IllegalStateException.class, rootCause);
            assertEquals("Duplicate vendor adapter registration.", duplicateFailure.getMessage());
        });
    }

    private Throwable rootCause(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private record StubAdapter(String vendorKey) implements VendorAdapter {

        @Override
        public List<CanonicalMetricReading> collect(String externalReference) {
            return List.of();
        }
    }
}
