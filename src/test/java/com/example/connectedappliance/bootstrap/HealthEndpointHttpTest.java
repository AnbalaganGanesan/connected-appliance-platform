package com.example.connectedappliance.bootstrap;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = DatabaseIndependentTestSupport.AUTO_CONFIGURATION_EXCLUSIONS)
@AutoConfigureMockMvc
@Import(HealthEndpointHttpTest.ControlledHealthConfiguration.class)
class HealthEndpointHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ControlledHealthIndicator controlledHealthIndicator;

    @BeforeEach
    void resetHealth() {
        controlledHealthIndicator.setStatus(Status.UP);
    }

    @Test
    void returnsSanitizedUpHealthWithoutComponents() throws Exception {
        String response = mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertSanitized(response);
    }

    @Test
    void mapsDownHealthToServiceUnavailableWithoutExposingDetails() throws Exception {
        controlledHealthIndicator.setStatus(Status.DOWN);

        String response = mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertSanitized(response);
    }

    @Test
    void exposesOnlyHealthThroughActuatorWebEndpoints() throws Exception {
        mockMvc.perform(get("/actuator/info")).andExpect(status().isNotFound());
        mockMvc.perform(get("/actuator/metrics")).andExpect(status().isNotFound());
        mockMvc.perform(get("/actuator/env")).andExpect(status().isNotFound());
    }

    private static void assertSanitized(String response) {
        assertThat(response)
                .doesNotContain(
                        "components",
                        "controlledHealth",
                        "jdbc:postgresql",
                        "connected_appliance",
                        "username",
                        "password",
                        "exception",
                        "ControlledHealthIndicator",
                        "sensitive-test-detail");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ControlledHealthConfiguration {

        @Bean
        ControlledHealthIndicator controlledHealthIndicator() {
            return new ControlledHealthIndicator();
        }
    }

    static class ControlledHealthIndicator implements HealthIndicator {

        private final AtomicReference<Status> status = new AtomicReference<>(Status.UP);

        void setStatus(Status status) {
            this.status.set(status);
        }

        @Override
        public Health health() {
            return Health.status(status.get())
                    .withDetail("jdbcUrl", "jdbc:postgresql://sensitive-test-detail")
                    .withDetail("username", "sensitive-test-detail")
                    .withDetail("implementation", getClass().getName())
                    .build();
        }
    }
}
