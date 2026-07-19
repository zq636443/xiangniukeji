package com.xniu.rental.pay.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DeductRecord(
    Long id,
    String deductNo,
    String batchNo,
    Long billId,
    Long orderId,
    Long agreementId,
    String agreementNo,
    Long paymentId,
    DeductStatus deductStatus,
    BigDecimal deductAmount,
    Integer retryCount,
    LocalDateTime nextRetryAt,
    String alipayTradeNo,
    String lastError,
    LocalDateTime requestedAt,
    LocalDateTime successAt,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
