package com.example.connectedappliance.appliance.application.port.in;

import java.util.UUID;

/** Narrow Appliance-owned query contract for cross-module existence checks. */
public interface ApplianceExistenceQueryPort {

    boolean exists(UUID applianceId);
}
