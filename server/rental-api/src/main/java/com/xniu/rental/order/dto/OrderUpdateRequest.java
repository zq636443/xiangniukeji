package com.xniu.rental.order.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderUpdateRequest(
    Long userAccountId,
    String customerName,
    String customerPhone,
    Long storeSkuId,
    Long packageId,
    Long frameAssetId,
    Long batteryAssetId,
    LocalDateTime orderedAt,
    @DecimalMin(value = "0.00", message = "实际核销金额不能小于 0")
    @Digits(integer = 10, fraction = 2, message = "实际核销金额最多保留 2 位小数")
    BigDecimal verificationAmount
) {
}
