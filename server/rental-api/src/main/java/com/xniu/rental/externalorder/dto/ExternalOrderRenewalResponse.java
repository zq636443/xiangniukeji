package com.xniu.rental.externalorder.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ExternalOrderRenewalResponse(
    Long id,
    Long externalOrderId,
    String eventNo,
    String externalOrderRecordNo,
    Long merchantId,
    Long storeId,
    Integer periodNo,
    LocalDateTime periodStartAt,
    LocalDateTime periodEndAt,
    BigDecimal renewalAmount,
    BigDecimal batteryCostAmount,
    String eventStatus,
    Boolean includedInMerchantStatement,
    LocalDateTime occurredAt
) {
}
