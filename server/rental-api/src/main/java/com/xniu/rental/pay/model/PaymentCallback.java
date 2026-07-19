package com.xniu.rental.pay.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentCallback(
    Long id,
    Long paymentId,
    String notifyId,
    String outTradeNo,
    String alipayTradeNo,
    String tradeStatus,
    BigDecimal totalAmount,
    Boolean verified,
    Boolean processed,
    String rawPayload,
    String failureReason,
    LocalDateTime receivedAt
) {
}
