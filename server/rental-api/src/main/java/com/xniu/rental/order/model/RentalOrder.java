package com.xniu.rental.order.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RentalOrder(
    Long id,
    String orderNo,
    Long userAccountId,
    Long merchantId,
    Long storeId,
    Long storeSkuId,
    Long skuId,
    Long packageId,
    Long frameAssetId,
    Long batteryAssetId,
    OrderStatus orderStatus,
    BigDecimal rentalAmount,
    BigDecimal signFeeAmount,
    BigDecimal depositAmount,
    BigDecimal payableAmount,
    BigDecimal paidAmount,
    Long settlementSnapshotId,
    String leaseUnit,
    Integer leaseValue,
    Integer totalPeriods,
    String billDayMode,
    Integer billDay,
    LocalDateTime expectedPickupAt,
    LocalDateTime leaseStartedAt,
    LocalDateTime expectedReturnAt,
    LocalDateTime returnedAt,
    LocalDateTime cancelledAt,
    String cancelReason,
    String exceptionReason,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
