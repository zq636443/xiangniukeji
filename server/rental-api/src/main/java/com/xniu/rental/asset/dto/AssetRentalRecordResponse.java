package com.xniu.rental.asset.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record AssetRentalRecordResponse(
    String recordType,
    Long orderId,
    String orderNo,
    String sourcePlatform,
    String externalOrderNo,
    Long userAccountId,
    Long storeId,
    String customerName,
    String customerPhone,
    String orderStatus,
    Long frameAssetId,
    Long batteryAssetId,
    BigDecimal rentalAmount,
    BigDecimal verificationAmount,
    BigDecimal signFeeAmount,
    BigDecimal paidAmount,
    String leaseUnit,
    Integer leaseValue,
    Integer totalPeriods,
    LocalDateTime leaseStartedAt,
    LocalDateTime expectedReturnAt,
    LocalDateTime returnedAt,
    LocalDateTime createdAt,
    List<AssetRentalBillResponse> bills
) {
}
