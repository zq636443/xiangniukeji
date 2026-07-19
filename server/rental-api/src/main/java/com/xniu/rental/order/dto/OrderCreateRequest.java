package com.xniu.rental.order.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public record OrderCreateRequest(
    Long userAccountId,
    @NotNull(message = "请选择门店商品") Long storeSkuId,
    @NotNull(message = "请选择套餐") Long packageId,
    Long frameAssetId,
    Long batteryAssetId,
    LocalDateTime expectedPickupAt
) {
}
