package com.example.connectedappliance.appliance.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.example.connectedappliance.shared.validation.TrimmedSize;

/** Public registration request containing only approved vendor-neutral fields. */
public record RegisterApplianceRequest(
        @NotBlank @TrimmedSize(min = 1, max = 100) String displayName,
        @TrimmedSize(max = 500) String description,
        @NotNull @Size(min = 1, max = 50) @Pattern(regexp = "[a-z0-9-]+") String vendorKey,
        @NotBlank @Size(max = 128) String externalReference,
        @NotNull @Min(5) @Max(86_400) Integer collectionIntervalSeconds) {
}
