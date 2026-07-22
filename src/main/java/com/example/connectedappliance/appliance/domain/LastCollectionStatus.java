package com.example.connectedappliance.appliance.domain;

/** Latest completed collection status retained on an appliance. */
public enum LastCollectionStatus {
    NEVER_ATTEMPTED,
    SUCCESS,
    PARTIAL_SUCCESS,
    FAILED
}
