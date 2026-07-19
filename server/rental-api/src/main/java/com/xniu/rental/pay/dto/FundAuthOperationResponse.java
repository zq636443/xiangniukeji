package com.xniu.rental.pay.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FundAuthOperationResponse(
    Long id,
    String operationNo,
    Long authOrderId,
    Long billId,
    Long paymentId,
    String operationType,
    String operationStatus,
    BigDecimal amount,
    String outRequestNo,
    String alipayTradeNo,
    String alipayOperationId,
    String remark,
    String failureReason,
    LocalDateTime createdAt
) {
}
