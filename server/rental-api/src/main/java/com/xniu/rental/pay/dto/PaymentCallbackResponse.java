package com.xniu.rental.pay.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentCallbackResponse(
    Long id,
    Long paymentId,
    String notifyId,
    String outTradeNo,
    String alipayTradeNo,
    String tradeStatus,
    BigDecimal totalAmount,
    Boolean verified,
    Boolean processed,
    String failureReason,
    LocalDateTime receivedAt
) {
}
