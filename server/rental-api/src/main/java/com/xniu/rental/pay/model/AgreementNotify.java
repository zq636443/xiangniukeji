package com.xniu.rental.pay.model;

import java.time.LocalDateTime;

public record AgreementNotify(
    Long id,
    Long agreementId,
    String notifyId,
    String externalAgreementNo,
    String agreementNo,
    String agreementStatus,
    Boolean verified,
    Boolean processed,
    String rawPayload,
    String failureReason,
    LocalDateTime receivedAt
) {
}
