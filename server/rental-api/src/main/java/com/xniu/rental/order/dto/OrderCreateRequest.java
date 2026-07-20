package com.xniu.rental.order.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public record OrderCreateRequest(
    Long userAccountId,
    String customerName,
    String customerPhone,
    @NotNull(message = "请选择门店商品") Long storeSkuId,
    @NotNull(message = "请选择 SKU") Long packageId,
    Long frameAssetId,
    Long batteryAssetId,
    LocalDateTime expectedPickupAt,
    LocalDateTime orderedAt
) {
    public OrderCreateRequest(
        Long userAccountId,
        String customerName,
        String customerPhone,
        Long storeSkuId,
        Long packageId,
        Long frameAssetId,
        Long batteryAssetId,
        LocalDateTime expectedPickupAt
    ) {
        this(userAccountId, customerName, customerPhone, storeSkuId, packageId, frameAssetId, batteryAssetId, expectedPickupAt, null);
    }

    public OrderCreateRequest(
        Long userAccountId,
        Long storeSkuId,
        Long packageId,
        Long frameAssetId,
        Long batteryAssetId,
        LocalDateTime expectedPickupAt
    ) {
        this(userAccountId, null, null, storeSkuId, packageId, frameAssetId, batteryAssetId, expectedPickupAt, null);
    }
}
