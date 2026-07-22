package com.example.connectedappliance.shared.observability;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import com.example.connectedappliance.bootstrap.DatabaseIndependentTestSupport;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = DatabaseIndependentTestSupport.AUTO_CONFIGURATION_EXCLUSIONS)
@AutoConfigureMockMvc
class CorrelationHealthEndpointTest {

    private static final String UUID_PATTERN =
            "[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}";

    @Autowired
    private MockMvc mockMvc;

    @AfterEach
    void correlationMdcIsEmpty() {
        org.assertj.core.api.Assertions.assertThat(
                MDC.get(CorrelationIdConstants.MDC_KEY)).isNull();
    }

    @Test
    void healthEchoesValidSuppliedCorrelationId() throws Exception {
        mockMvc.perform(get("/actuator/health")
                        .header(CorrelationIdConstants.HEADER_NAME, "health-review-1"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        CorrelationIdConstants.HEADER_NAME, "health-review-1"))
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void healthGeneratesCorrelationIdWhenMissing() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        CorrelationIdConstants.HEADER_NAME,
                        matchesPattern(UUID_PATTERN)))
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void invalidCorrelationIdStopsBeforeHealthEndpoint() throws Exception {
        mockMvc.perform(get("/actuator/health")
                        .header(CorrelationIdConstants.HEADER_NAME, "invalid health id"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(
                        CorrelationIdConstants.HEADER_NAME,
                        matchesPattern(UUID_PATTERN)))
                .andExpect(jsonPath("$.code").value("INVALID_CORRELATION_ID"))
                .andExpect(jsonPath("$.correlationId", matchesPattern(UUID_PATTERN)));
    }
}
