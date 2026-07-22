package com.xniu.rental.asset.model;

public enum AssetType {
    VEHICLE_FRAME,
    BATTERY,
    INTEGRATED_VEHICLE,
    GENERAL;

    public boolean canBindAs(AssetType slotType) {
        return this == slotType || (slotType == VEHICLE_FRAME && this == INTEGRATED_VEHICLE);
    }

    public boolean isIntegratedVehicle() {
        return this == INTEGRATED_VEHICLE;
    }
}
