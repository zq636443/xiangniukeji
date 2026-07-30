package com.xniu.rental.externalorder.dto;

import com.xniu.rental.pricing.dto.RenewalPricingRuleResponse;
import java.time.LocalDateTime;

public record ExternalOrderPricingRevisionResponse(
    Long id,
    Long externalOrderId,
    String batchNo,
    String revisionStatus,
    Boolean requiresCustomerConfirmation,
    RenewalPricingRuleResponse previousRule,
    RenewalPricingRuleResponse newRule,
    String reason,
    String confirmationMethod,
    String confirmationReference,
    Long operatorAccountId,
    LocalDateTime customerConfirmedAt,
    LocalDateTime appliedAt,
    LocalDateTime createdAt
) {
}
