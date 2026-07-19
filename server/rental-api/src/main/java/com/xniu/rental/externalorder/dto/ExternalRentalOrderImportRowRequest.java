package com.xniu.rental.externalorder.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ExternalRentalOrderImportRowRequest(
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
    BigDecimal signFeeAmount,
    BigDecimal depositAmount,
    String remark
) {
}
