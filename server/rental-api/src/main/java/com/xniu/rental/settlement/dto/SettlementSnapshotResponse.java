package com.xniu.rental.settlement.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SettlementSnapshotResponse(
    Long id,
    String snapshotNo,
    String sourceType,
    Long sourceId,
    Long storeSkuId,
    Long skuId,
    Long merchantId,
    Long storeId,
    Long frameAssetId,
    Long batteryAssetId,
    Long matchedRuleId,
    String matchedRuleScope,
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
