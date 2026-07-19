package com.xniu.rental.pay.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FundAuthNotifyResponse(
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
    String failureReason,
    LocalDateTime receivedAt
) {
}
