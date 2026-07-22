package com.example.connectedappliance.bootstrap;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = DatabaseIndependentTestSupport.AUTO_CONFIGURATION_EXCLUSIONS)
class UtcClockConfigurationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void providesExactlyOneUtcClockBean() {
        Map<String, Clock> clocks = applicationContext.getBeansOfType(Clock.class);

        assertThat(clocks).hasSize(1);
        assertThat(clocks.values().iterator().next().getZone()).isEqualTo(ZoneOffset.UTC);
    }
}
