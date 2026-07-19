package com.xniu.rental.settlement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record SnapshotCreateRequest(
    @NotBlank(message = "请选择来源类型") String sourceType,
    Long sourceId,
    @NotNull(message = "请选择门店商品") Long storeSkuId,
    Long frameAssetId,
    Long batteryAssetId,
    @NotNull(message = "请输入租金金额") BigDecimal rentalAmount
) {
}
