package com.xniu.rental.asset.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record InvestorAssetRequest(
    Long assetTypeId,
    String assetType,
    @NotBlank(message = "请输入资产编号") String serialNo,
    Long currentMerchantId,
    Long currentStoreId,
    @NotNull(message = "请输入采购金额") BigDecimal purchaseAmount,
    BigDecimal residualValue,
    LocalDate purchasedAt,
    String arrivalBatchNo
) {
    public InvestorAssetRequest(
        Long assetTypeId,
        String assetType,
        String serialNo,
        Long currentMerchantId,
        Long currentStoreId,
        BigDecimal purchaseAmount,
        BigDecimal residualValue,
        LocalDate purchasedAt
    ) {
        this(assetTypeId, assetType, serialNo, currentMerchantId, currentStoreId, purchaseAmount, residualValue, purchasedAt, null);
    }
}
