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
    @NotNull(message = "请输入结算基数") BigDecimal rentalAmount,
    String sourceChannel,
    BigDecimal signFeeAmount,
    BigDecimal batteryCostAmount
) {
    public SnapshotCreateRequest(
        String sourceType,
        Long sourceId,
        Long storeSkuId,
        Long frameAssetId,
        Long batteryAssetId,
        BigDecimal rentalAmount,
        String sourceChannel
    ) {
        this(sourceType, sourceId, storeSkuId, frameAssetId, batteryAssetId, rentalAmount, sourceChannel, null, null);
    }

    public SnapshotCreateRequest(
        String sourceType,
        Long sourceId,
        Long storeSkuId,
        Long frameAssetId,
        Long batteryAssetId,
        BigDecimal rentalAmount,
        String sourceChannel,
        BigDecimal signFeeAmount
    ) {
        this(sourceType, sourceId, storeSkuId, frameAssetId, batteryAssetId, rentalAmount, sourceChannel, signFeeAmount, null);
    }
}
