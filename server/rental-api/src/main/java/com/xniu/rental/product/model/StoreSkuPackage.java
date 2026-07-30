package com.xniu.rental.product.model;

import java.math.BigDecimal;
import com.xniu.rental.pricing.model.RenewalBillingMode;

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
    RenewalBillingMode renewalBillingMode,
    BigDecimal renewalDailyAmount,
    Boolean renewalDailyCapEnabled,
    Integer renewalGraceHours,
    BigDecimal overdueDailyAmount,
    ProductStatus status
) {
}
