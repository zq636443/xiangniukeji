package com.xniu.rental.pricing.dto;

import java.math.BigDecimal;

public record RenewalPricingRuleResponse(
    Boolean autoRenewEnabled,
    String renewalUnit,
    Integer renewalValue,
    BigDecimal renewalAmount,
    String renewalBillingMode,
    BigDecimal renewalDailyAmount,
    Boolean renewalDailyCapEnabled,
    Integer renewalGraceHours,
    BigDecimal overdueDailyAmount
) {
}
