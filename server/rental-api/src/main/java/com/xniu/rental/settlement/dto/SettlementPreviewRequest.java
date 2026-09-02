package com.xniu.rental.settlement.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record SettlementPreviewRequest(
    @NotNull(message = "请选择门店商品") Long storeSkuId,
    Long frameAssetId,
    Long batteryAssetId,
    @NotNull(message = "请输入结算基数") BigDecimal rentalAmount,
    String sourceChannel,
    BigDecimal batteryCostAmount
) {
    public SettlementPreviewRequest(
        Long storeSkuId,
        Long frameAssetId,
        Long batteryAssetId,
        BigDecimal rentalAmount,
        String sourceChannel
    ) {
        this(storeSkuId, frameAssetId, batteryAssetId, rentalAmount, sourceChannel, null);
    }
}
