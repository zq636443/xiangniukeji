package com.xniu.rental.asset.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record AssetMaintenanceRequest(
    @NotNull(message = "请选择资产") Long assetId,
    Long orderId,
    Long storeId,
    @NotBlank(message = "请选择维修类型") String maintenanceType,
    String maintenanceStatus,
    String responsibilityType,
    LocalDateTime startedAt,
    LocalDateTime completedAt,
    BigDecimal laborCost,
    BigDecimal externalCost,
    String costBearerType,
    Long costBearerId,
    String remark,
    @Valid List<AssetMaintenancePartRequest> parts
) {
}
