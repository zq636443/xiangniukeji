package com.xniu.rental.externalorder.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ExternalRentalOrderImportRowRequest(
    Integer lineNo,
    String sourcePlatform,
    String externalOrderNo,
    Long storeSkuId,
    Long packageId,
    Integer leaseMultiplier,
    String customerName,
    String customerPhone,
    LocalDateTime rentStartedAt,
    LocalDateTime expectedReturnAt,
    Long frameAssetId,
    Long batteryAssetId,
    BigDecimal externalRentalAmount,
    BigDecimal verificationAmount,
    BigDecimal signFeeAmount,
    BigDecimal depositAmount,
    String remark
) {
    public ExternalRentalOrderImportRowRequest(
        Integer lineNo,
        String sourcePlatform,
        String externalOrderNo,
        Long storeSkuId,
        Long packageId,
        String customerName,
        String customerPhone,
        LocalDateTime rentStartedAt,
        LocalDateTime expectedReturnAt,
        Long frameAssetId,
        Long batteryAssetId,
        BigDecimal externalRentalAmount,
        BigDecimal verificationAmount,
        BigDecimal signFeeAmount,
        BigDecimal depositAmount,
        String remark
    ) {
        this(lineNo, sourcePlatform, externalOrderNo, storeSkuId, packageId, null, customerName, customerPhone,
            rentStartedAt, expectedReturnAt, frameAssetId, batteryAssetId, externalRentalAmount, verificationAmount,
            signFeeAmount, depositAmount, remark);
    }
}
