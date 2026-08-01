package com.xniu.rental.asset.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record AssetResponse(
    Long id,
    String assetCode,
    String assetType,
    Long assetTypeId,
    String assetTypeCode,
    String assetTypeName,
    String serialLabel,
    String serialNo,
    String arrivalBatchNo,
    Long investorId,
    String investorName,
    Long currentMerchantId,
    String merchantName,
    Long currentStoreId,
    String storeName,
    String status,
    BigDecimal purchaseAmount,
    BigDecimal maintenanceFeeAmount,
    BigDecimal residualValue,
    LocalDate purchasedAt,
    LocalDateTime scrappedAt,
    LocalDateTime soldAt
) {
}
