package com.xniu.rental.asset.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record InvestorAssetUpdateRequest(
    @NotNull(message = "请选择资产类型") Long assetTypeId,
    @NotBlank(message = "请输入资产编号") String serialNo,
    @NotNull(message = "请输入采购金额") BigDecimal purchaseAmount,
    BigDecimal residualValue,
    LocalDate purchasedAt
) {
}
