package com.example.connectedappliance.appliance.api;

import jakarta.validation.constraints.NotBlank;

import com.example.connectedappliance.shared.validation.TrimmedSize;

/** Full replacement of public Appliance display metadata. */
public record UpdateApplianceMetadataRequest(
        @NotBlank @TrimmedSize(min = 1, max = 100) String displayName,
        @TrimmedSize(max = 500) String description) {
}
