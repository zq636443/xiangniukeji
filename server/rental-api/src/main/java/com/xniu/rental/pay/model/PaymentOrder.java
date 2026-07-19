package com.xniu.rental.pay.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentOrder(
    Long id,
    String paymentNo,
    Long billId,
    Long orderId,
    Long userAccountId,
    Long merchantId,
    Long storeId,
    PayChannel payChannel,
    PayStatus payStatus,
    BigDecimal payAmount,
    BigDecimal paidAmount,
    String subject,
    String payerAlipayUserId,
    String alipayTradeNo,
    BigDecimal refundAmount,
    LocalDateTime paidAt,
    LocalDateTime closedAt,
    String lastError,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
