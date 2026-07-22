package com.example.connectedappliance.bootstrap;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class UtcClockConfiguration {

    @Bean
    Clock utcClock() {
        return Clock.systemUTC();
    }
}
