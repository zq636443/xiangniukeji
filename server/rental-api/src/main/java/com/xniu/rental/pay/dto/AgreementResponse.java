package com.xniu.rental.pay.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AgreementResponse(
    Long id,
    String agreementNo,
    String externalAgreementNo,
    Long userAccountId,
    String alipayUserId,
    Long orderId,
    Long merchantId,
    Long storeId,
    String agreementType,
    String agreementStatus,
    String personalProductCode,
    String signScene,
    BigDecimal maxSingleAmount,
    LocalDateTime signTime,
    LocalDateTime validTime,
    LocalDateTime invalidTime,
    String lastError,
    LocalDateTime createdAt
) {
}
