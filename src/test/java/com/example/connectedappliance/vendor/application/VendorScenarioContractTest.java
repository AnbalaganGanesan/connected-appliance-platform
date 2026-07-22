package com.example.connectedappliance.vendor.application;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VendorScenarioContractTest {

    @Test
    void exposesExactlyTheApprovedDeterministicScenarios() {
        assertEquals(
                List.of(
                        "SUCCESS",
                        "PARTIAL",
                        "TIMEOUT",
                        "RATE_LIMITED",
                        "INVALID_DATA",
                        "TRANSIENT",
                        "UNEXPECTED"),
                java.util.Arrays.stream(VendorScenario.values()).map(Enum::name).toList());
    }
}
