package com.example.connectedappliance.integration;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Controlled Task 10 integration-test clock kept separate from Testcontainers support. */
@TestConfiguration(proxyBeanMethods = false)
class Task10FixedClockConfiguration {

    static final Instant REGISTRATION_TIME = Instant.parse("2026-07-21T10:00:00Z");

    @Bean
    @Primary
    Clock taskTenFixedClock() {
        return Clock.fixed(REGISTRATION_TIME, ZoneOffset.UTC);
    }
}
