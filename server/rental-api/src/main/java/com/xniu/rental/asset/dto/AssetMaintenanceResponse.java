package com.xniu.rental.asset.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record AssetMaintenanceResponse(
    Long id,
    String maintenanceNo,
    Long assetId,
    String assetCode,
    String assetType,
    String serialNo,
    Long orderId,
    Long storeId,
    String maintenanceType,
    String maintenanceStatus,
    String responsibilityType,
    LocalDateTime startedAt,
    LocalDateTime completedAt,
    BigDecimal laborCost,
    BigDecimal externalCost,
    BigDecimal partsCost,
    BigDecimal totalCost,
    BigDecimal merchantReimbursementAmount,
    BigDecimal investorDeductAmount,
    BigDecimal customerChargeAmount,
    String costBearerType,
    Long costBearerId,
    Long operatorAccountId,
    String remark,
    LocalDateTime createdAt,
    List<AssetMaintenancePartResponse> parts
) {
}
