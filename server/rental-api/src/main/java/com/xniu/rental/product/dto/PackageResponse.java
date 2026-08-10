package com.xniu.rental.product.dto;

import java.math.BigDecimal;

public record PackageResponse(
    Long id,
    String packageCode,
    Long skuId,
    String skuName,
    String packageName,
    BigDecimal priceAmount,
    BigDecimal signFeeAmount,
    String leaseUnit,
    Integer leaseValue,
    Integer totalPeriods,
    String billDayMode,
    Integer billDay,
    String status
) {
}
