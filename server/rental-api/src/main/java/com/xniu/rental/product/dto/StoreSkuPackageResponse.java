package com.xniu.rental.product.dto;

import java.math.BigDecimal;

public record StoreSkuPackageResponse(
    Long id,
    Long packageId,
    String packageName,
    String leaseUnit,
    Integer leaseValue,
    Integer totalPeriods,
    String billDayMode,
    Integer billDay,
    BigDecimal rentalAmount,
    BigDecimal periodAmount,
    BigDecimal depositAmount,
    String status
) {
}
