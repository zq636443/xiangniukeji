package com.xniu.rental.settlement.model;

public enum SettlementCalculationVersion {
    LEGACY_V1,
    PROFIT_V2,
    PROFIT_V3;

    public boolean usesProfitSharing() {
        return this != LEGACY_V1;
    }

    public boolean usesGrossChannelReferral() {
        return this == PROFIT_V3;
    }
}
