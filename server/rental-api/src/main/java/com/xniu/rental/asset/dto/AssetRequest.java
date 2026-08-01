package com.xniu.rental.asset.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record AssetRequest(
    Long assetTypeId,
    String assetType,
    @NotBlank(message = "请输入资产编号") String serialNo,
    @NotNull(message = "请选择出资方") Long investorId,
    Long currentMerchantId,
    Long currentStoreId,
    @NotNull(message = "请输入采购金额") BigDecimal purchaseAmount,
    BigDecimal maintenanceFeeAmount,
    BigDecimal residualValue,
    LocalDate purchasedAt,
    String arrivalBatchNo
) {
    public AssetRequest(
        String assetType,
        String serialNo,
        Long investorId,
        Long currentMerchantId,
        Long currentStoreId,
        BigDecimal purchaseAmount,
        BigDecimal maintenanceFeeAmount,
        BigDecimal residualValue,
        LocalDate purchasedAt
    ) {
        this(null, assetType, serialNo, investorId, currentMerchantId, currentStoreId, purchaseAmount, maintenanceFeeAmount, residualValue, purchasedAt, null);
    }

    public AssetRequest(
        Long assetTypeId,
        String assetType,
        String serialNo,
        Long investorId,
        Long currentMerchantId,
        Long currentStoreId,
        BigDecimal purchaseAmount,
        BigDecimal maintenanceFeeAmount,
        BigDecimal residualValue,
        LocalDate purchasedAt
    ) {
        this(assetTypeId, assetType, serialNo, investorId, currentMerchantId, currentStoreId, purchaseAmount, maintenanceFeeAmount, residualValue, purchasedAt, null);
    }
}
