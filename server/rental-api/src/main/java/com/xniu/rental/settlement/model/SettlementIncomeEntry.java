package com.xniu.rental.settlement.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SettlementIncomeEntry(
    Long id,
    String entryNo,
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
    LocalDateTime settledAt,
    LocalDateTime createdAt
) {
}
