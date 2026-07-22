package com.xniu.rental.asset.model;

public enum AssetType {
    VEHICLE_FRAME,
    BATTERY,
    INTEGRATED_VEHICLE,
    GENERAL;

    public boolean canBindAs(AssetType slotType) {
        if (slotType == VEHICLE_FRAME) {
            return this != BATTERY;
        }
        return this == slotType;
    }

    public boolean isIntegratedVehicle() {
        return this == INTEGRATED_VEHICLE;
    }
}
