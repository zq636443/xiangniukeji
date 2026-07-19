package com.xniu.rental.asset.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record AssetItem(
    Long id,
    String assetCode,
    AssetType assetType,
    String serialNo,
    Long investorId,
    Long currentMerchantId,
    Long currentStoreId,
    AssetStatus status,
    BigDecimal purchaseAmount,
    BigDecimal maintenanceFeeAmount,
    BigDecimal residualValue,
    LocalDate purchasedAt,
    LocalDateTime scrappedAt,
    LocalDateTime soldAt,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
