package com.xniu.rental.pay.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FundAuthOperation(
    Long id,
    String operationNo,
    Long authOrderId,
    Long billId,
    Long paymentId,
    FundAuthOperationType operationType,
    FundAuthOperationStatus operationStatus,
    BigDecimal amount,
    String outRequestNo,
    String alipayTradeNo,
    String alipayOperationId,
    String remark,
    String failureReason,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
