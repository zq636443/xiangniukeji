package com.xniu.rental.settlement.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SettlementStatementLineResponse(
    Long id,
    Long statementId,
    String lineNo,
    String sourceType,
    Long sourceId,
    Long orderId,
    Long billId,
    Long assetId,
    Long merchantId,
    Long storeId,
    Long investorId,
    String lineType,
    BigDecimal amount,
    LocalDateTime occurredAt,
    String remark,
    LocalDateTime createdAt
) {
}
