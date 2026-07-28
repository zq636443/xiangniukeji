package com.xniu.rental.order.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
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
    LocalDateTime orderedAt,
    @Min(value = 1, message = "租期倍数不能小于 1")
    @Max(value = 120, message = "租期倍数不能大于 120")
    Integer leaseMultiplier,
    @DecimalMin(value = "0.00", message = "实际核销金额不能小于 0")
    @Digits(integer = 10, fraction = 2, message = "实际核销金额最多保留 2 位小数")
    BigDecimal verificationAmount
) {
    public OrderCreateRequest(
        Long userAccountId,
        String customerName,
        String customerPhone,
        Long storeSkuId,
        Long packageId,
        Long frameAssetId,
        Long batteryAssetId,
        LocalDateTime expectedPickupAt,
        LocalDateTime orderedAt,
        BigDecimal verificationAmount
    ) {
        this(userAccountId, customerName, customerPhone, storeSkuId, packageId, frameAssetId, batteryAssetId,
            expectedPickupAt, orderedAt, null, verificationAmount);
    }

    public OrderCreateRequest(
        Long userAccountId,
        String customerName,
        String customerPhone,
        Long storeSkuId,
        Long packageId,
        Long frameAssetId,
        Long batteryAssetId,
        LocalDateTime expectedPickupAt,
        LocalDateTime orderedAt
    ) {
        this(userAccountId, customerName, customerPhone, storeSkuId, packageId, frameAssetId, batteryAssetId, expectedPickupAt, orderedAt, null, null);
    }

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
        this(userAccountId, customerName, customerPhone, storeSkuId, packageId, frameAssetId, batteryAssetId, expectedPickupAt, null, null, null);
    }

    public OrderCreateRequest(
        Long userAccountId,
        Long storeSkuId,
        Long packageId,
        Long frameAssetId,
        Long batteryAssetId,
        LocalDateTime expectedPickupAt
    ) {
        this(userAccountId, null, null, storeSkuId, packageId, frameAssetId, batteryAssetId, expectedPickupAt, null, null, null);
    }
}
