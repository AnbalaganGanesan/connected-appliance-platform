package com.example.connectedappliance.appliance.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Full replacement of an Appliance desired collection state. */
public record UpdateCollectionStateRequest(
        @NotBlank
        @Pattern(regexp = "(?:\\s*|ACTIVE|PAUSED)")
        String collectionState) {
}
