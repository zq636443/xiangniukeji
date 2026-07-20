package com.xniu.rental.product.model;

import java.math.BigDecimal;

public record StoreSkuPackage(
    Long id,
    Long storeSkuId,
    Long packageId,
    BigDecimal rentalAmount,
    BigDecimal periodAmount,
    BigDecimal depositAmount,
    Boolean autoRenewEnabled,
    LeaseUnit renewalUnit,
    Integer renewalValue,
    BigDecimal renewalAmount,
    ProductStatus status
) {
}
