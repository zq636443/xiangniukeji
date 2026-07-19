package com.xniu.rental.pay.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PayAgreement(
    Long id,
    String agreementNo,
    String externalAgreementNo,
    Long userAccountId,
    String alipayUserId,
    Long orderId,
    Long merchantId,
    Long storeId,
    AgreementType agreementType,
    AgreementStatus agreementStatus,
    String personalProductCode,
    String signScene,
    BigDecimal maxSingleAmount,
    String signUrl,
    LocalDateTime signTime,
    LocalDateTime validTime,
    LocalDateTime invalidTime,
    String lastError,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
