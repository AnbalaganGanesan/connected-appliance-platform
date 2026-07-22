package com.example.connectedappliance.vendor.infrastructure.mockalpha;

import java.time.Duration;

import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import com.example.connectedappliance.vendor.application.VendorScenario;

/** Immutable, validated scenario and delay configuration for Mock Alpha. */
@Validated
@ConfigurationProperties(prefix = "app.mock-vendors.mock-alpha")
public record MockAlphaProperties(
        @NotNull VendorScenario scenario,
        @NotNull @DurationMin(seconds = 0, inclusive = true) Duration delay) {}
