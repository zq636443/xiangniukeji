package com.xniu.rental.pricing.dto;

import java.time.LocalDateTime;

public record OrderPricingRevisionResponse(
    Long id,
    Long orderId,
    String revisionStatus,
    Boolean requiresCustomerConfirmation,
    String effectiveMode,
    RenewalPricingRuleResponse previousRule,
    RenewalPricingRuleResponse newRule,
    String reason,
    Long operatorAccountId,
    LocalDateTime customerConfirmedAt,
    LocalDateTime appliedAt,
    LocalDateTime createdAt
) {
}
