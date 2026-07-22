package com.example.connectedappliance.vendor.infrastructure.mockbeta;

import java.time.Duration;

import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import com.example.connectedappliance.vendor.application.VendorScenario;

/** Immutable, validated scenario and delay configuration for Mock Beta. */
@Validated
@ConfigurationProperties(prefix = "app.mock-vendors.mock-beta")
public record MockBetaProperties(
        @NotNull VendorScenario scenario,
        @NotNull @DurationMin(seconds = 0, inclusive = true) Duration delay) {}
