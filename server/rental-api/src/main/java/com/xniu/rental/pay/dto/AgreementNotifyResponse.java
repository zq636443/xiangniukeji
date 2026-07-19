package com.xniu.rental.pay.dto;

import java.time.LocalDateTime;

public record AgreementNotifyResponse(
    Long id,
    Long agreementId,
    String notifyId,
    String externalAgreementNo,
    String agreementNo,
    String agreementStatus,
    Boolean verified,
    Boolean processed,
    String failureReason,
    LocalDateTime receivedAt
) {
}
