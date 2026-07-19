package com.xniu.rental.settlement.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SettlementRuleSnapshot(
    Long id,
    String snapshotNo,
    SnapshotSourceType sourceType,
    Long sourceId,
    Long storeSkuId,
    Long skuId,
    Long merchantId,
    Long storeId,
    Long frameAssetId,
    Long batteryAssetId,
    Long matchedRuleId,
    RuleScope matchedRuleScope,
    BigDecimal rentalAmount,
    BigDecimal signFeeAmount,
    BigDecimal merchantOrderFeeAmount,
    BigDecimal merchantRentShareRate,
    BigDecimal merchantRentShareAmount,
    BigDecimal platformRentShareRate,
    BigDecimal platformRentShareAmount,
    BigDecimal investorRentShareRate,
    BigDecimal investorGrossShareAmount,
    BigDecimal investorOperationFeeAmount,
    BigDecimal maintenanceFeeAmount,
    BigDecimal investorNetShareAmount,
    String ruleSummary,
    LocalDateTime createdAt
) {
}
