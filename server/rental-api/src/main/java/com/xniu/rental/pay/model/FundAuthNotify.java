package com.xniu.rental.pay.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FundAuthNotify(
    Long id,
    Long authOrderId,
    String notifyId,
    String outOrderNo,
    String outRequestNo,
    String authNo,
    String operationId,
    String authStatus,
    BigDecimal totalFreezeAmount,
    BigDecimal restAmount,
    Boolean verified,
    Boolean processed,
    String rawPayload,
    String failureReason,
    LocalDateTime receivedAt
) {
}
