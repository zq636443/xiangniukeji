package com.xniu.rental.pricing.model;

import java.time.LocalDateTime;

public record OrderPricingRevision(
    Long id,
    Long orderId,
    PricingRevisionStatus revisionStatus,
    Boolean requiresCustomerConfirmation,
    String effectiveMode,
    RenewalPricingRule previousRule,
    RenewalPricingRule newRule,
    String reason,
    Long operatorAccountId,
    LocalDateTime customerConfirmedAt,
    LocalDateTime appliedAt,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
