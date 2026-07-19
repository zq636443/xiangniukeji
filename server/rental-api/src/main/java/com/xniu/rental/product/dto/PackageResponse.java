package com.xniu.rental.product.dto;

public record PackageResponse(
    Long id,
    String packageCode,
    Long skuId,
    String skuName,
    String packageName,
    String leaseUnit,
    Integer leaseValue,
    Integer totalPeriods,
    String billDayMode,
    Integer billDay,
    String status
) {
}
