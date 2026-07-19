package com.xniu.rental.externalorder.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ExternalRentalOrder(
    Long id,
    String recordNo,
    ExternalOrderSourcePlatform sourcePlatform,
    String externalOrderNo,
    Long merchantId,
    Long storeId,
    Long storeSkuId,
    Long skuId,
    Long packageId,
    String customerName,
    String customerPhone,
    Long frameAssetId,
    Long batteryAssetId,
    ExternalRentalOrderStatus orderStatus,
    BigDecimal externalRentalAmount,
    BigDecimal signFeeAmount,
    BigDecimal depositAmount,
    String leaseUnit,
    Integer leaseValue,
    Integer totalPeriods,
    LocalDateTime rentStartedAt,
    LocalDateTime expectedReturnAt,
    LocalDateTime finishedAt,
    Long returnStoreId,
    String terminationReason,
    String remark,
    Long createdByAccountId,
    Long updatedByAccountId,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
