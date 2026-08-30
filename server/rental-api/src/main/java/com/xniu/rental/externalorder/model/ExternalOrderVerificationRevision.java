package com.xniu.rental.externalorder.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ExternalOrderVerificationRevision(
    Long id,
    Long externalOrderId,
    BigDecimal verificationAmount,
    LocalDateTime effectiveAt,
    ExternalOrderVerificationRevisionType revisionType,
    Long sourceSnapshotId,
    Long operatorAccountId,
    LocalDateTime createdAt
) {
}
