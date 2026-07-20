package com.xniu.rental.product.model;

import java.math.BigDecimal;

public record ProductPackage(
    Long id,
    String packageCode,
    Long skuId,
    String packageName,
    BigDecimal priceAmount,
    LeaseUnit leaseUnit,
    Integer leaseValue,
    Integer totalPeriods,
    BillDayMode billDayMode,
    Integer billDay,
    ProductStatus status
) {
}
