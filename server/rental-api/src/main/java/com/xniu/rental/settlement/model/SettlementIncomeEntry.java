package com.xniu.rental.settlement.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SettlementIncomeEntry(
    Long id,
    String entryNo,
    IncomeSourceType sourceType,
    Long sourceId,
    String sourceNo,
    Long orderId,
    Long snapshotId,
    Long merchantId,
    Long storeId,
    IncomeBeneficiaryType beneficiaryType,
    Long beneficiaryId,
    IncomeLineType lineType,
    BigDecimal amount,
    IncomeEntryStatus entryStatus,
    String remark,
    LocalDateTime occurredAt,
    LocalDateTime settledAt,
    LocalDateTime createdAt
) {
}
