package com.xniu.rental.externalorder.model;

import com.xniu.rental.asset.model.AssetType;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Frozen investor attribution for one asset slot and time segment. */
public record ExternalOrderRenewalInvestorAllocation(
    Long id,
    Long renewalEventId,
    Long settlementSnapshotId,
    AssetType assetType,
    Long assetId,
    Long investorId,
    LocalDateTime effectiveStartAt,
    LocalDateTime effectiveEndAt,
    BigDecimal allocationWeight,
    BigDecimal rentBaseAmount,
    BigDecimal investorShareAmount,
    LocalDateTime createdAt
) {
}
