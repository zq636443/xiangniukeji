package com.xniu.rental.pay.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentResponse(
    Long id,
    String paymentNo,
    Long billId,
    Long orderId,
    Long userAccountId,
    Long merchantId,
    Long storeId,
    String payChannel,
    String payStatus,
    BigDecimal payAmount,
    BigDecimal paidAmount,
    String subject,
    String payerAlipayUserId,
    String alipayTradeNo,
    BigDecimal refundAmount,
    LocalDateTime paidAt,
    LocalDateTime closedAt,
    String lastError,
    LocalDateTime createdAt
) {
}
