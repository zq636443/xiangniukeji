package com.xniu.rental.settlement.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SettlementStatementLine(
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
    SettlementStatementLineType lineType,
    BigDecimal amount,
    LocalDateTime occurredAt,
    String remark,
    LocalDateTime createdAt
) {
}
