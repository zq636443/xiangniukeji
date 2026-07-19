package com.xniu.rental.asset.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record AssetRequest(
    @NotBlank(message = "请选择资产类型") String assetType,
    @NotBlank(message = "请输入车架号或电池号") String serialNo,
    @NotNull(message = "请选择出资方") Long investorId,
    Long currentMerchantId,
    Long currentStoreId,
    @NotNull(message = "请输入采购金额") BigDecimal purchaseAmount,
    @NotNull(message = "请输入维保费") BigDecimal maintenanceFeeAmount,
    @NotNull(message = "请输入残值") BigDecimal residualValue,
    LocalDate purchasedAt
) {
}
