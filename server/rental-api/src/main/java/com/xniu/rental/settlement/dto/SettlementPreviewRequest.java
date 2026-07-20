package com.xniu.rental.settlement.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record SettlementPreviewRequest(
    @NotNull(message = "请选择门店商品") Long storeSkuId,
    Long frameAssetId,
    Long batteryAssetId,
    @NotNull(message = "请输入结算基数") BigDecimal rentalAmount,
    String sourceChannel
) {
}
