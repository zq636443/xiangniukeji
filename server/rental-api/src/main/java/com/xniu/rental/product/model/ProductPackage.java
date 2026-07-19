package com.xniu.rental.product.model;

public record ProductPackage(
    Long id,
    String packageCode,
    Long skuId,
    String packageName,
    LeaseUnit leaseUnit,
    Integer leaseValue,
    Integer totalPeriods,
    BillDayMode billDayMode,
    Integer billDay,
    ProductStatus status
) {
}
