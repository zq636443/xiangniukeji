package com.xniu.rental.pay.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DeductRecordResponse(
    Long id,
    String deductNo,
    String batchNo,
    Long billId,
    Long orderId,
    Long agreementId,
    String agreementNo,
    Long paymentId,
    String deductStatus,
    BigDecimal deductAmount,
    Integer retryCount,
    LocalDateTime nextRetryAt,
    String alipayTradeNo,
    String lastError,
    LocalDateTime requestedAt,
    LocalDateTime successAt,
    LocalDateTime createdAt
) {
}
