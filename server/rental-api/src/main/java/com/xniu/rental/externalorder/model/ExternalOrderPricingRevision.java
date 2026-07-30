package com.xniu.rental.externalorder.model;

import com.xniu.rental.pricing.model.PricingRevisionStatus;
import com.xniu.rental.pricing.model.RenewalPricingRule;
import java.time.LocalDateTime;

public record ExternalOrderPricingRevision(
    Long id,
    Long externalOrderId,
    String batchNo,
    PricingRevisionStatus revisionStatus,
    Boolean requiresCustomerConfirmation,
    RenewalPricingRule previousRule,
    RenewalPricingRule newRule,
    String reason,
    String confirmationMethod,
    String confirmationReference,
    Long operatorAccountId,
    LocalDateTime customerConfirmedAt,
    LocalDateTime appliedAt,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
