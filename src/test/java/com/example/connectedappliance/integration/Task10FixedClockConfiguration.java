package com.example.connectedappliance.integration;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Controlled integration-test clock kept separate from Testcontainers support. */
@TestConfiguration(proxyBeanMethods = false)
class Task10FixedClockConfiguration {

    static final Instant REGISTRATION_TIME = Instant.parse("2026-07-21T10:00:00Z");

    @Bean
    @Primary
    MutableUtcClock taskTenFixedClock() {
        return new MutableUtcClock(REGISTRATION_TIME);
    }

    static final class MutableUtcClock extends Clock {

        private final AtomicReference<Instant> current;

        MutableUtcClock(Instant initial) {
            current = new AtomicReference<>(Objects.requireNonNull(initial));
        }

        void set(Instant instant) {
            current.set(Objects.requireNonNull(instant));
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            if (!ZoneOffset.UTC.equals(zone)) {
                throw new IllegalArgumentException("Integration-test clock supports UTC only");
            }
            return this;
        }

        @Override
        public Instant instant() {
            return current.get();
        }
    }
}
