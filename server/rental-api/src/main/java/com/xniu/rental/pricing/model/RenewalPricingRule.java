package com.xniu.rental.pricing.model;

import java.math.BigDecimal;

public record RenewalPricingRule(
    Boolean autoRenewEnabled,
    String renewalUnit,
    Integer renewalValue,
    BigDecimal renewalAmount,
    RenewalBillingMode renewalBillingMode,
    BigDecimal renewalDailyAmount,
    Boolean renewalDailyCapEnabled,
    Integer renewalGraceHours,
    BigDecimal overdueDailyAmount
) {
}
