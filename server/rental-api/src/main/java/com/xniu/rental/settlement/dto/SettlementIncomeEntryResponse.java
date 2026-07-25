package com.xniu.rental.settlement.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SettlementIncomeEntryResponse(
    Long id,
    String entryNo,
    String sourceType,
    Long sourceId,
    String sourceNo,
    Long orderId,
    Long snapshotId,
    Long merchantId,
    Long storeId,
    String beneficiaryType,
    Long beneficiaryId,
    String lineType,
    BigDecimal amount,
    String entryStatus,
    String remark,
    LocalDateTime occurredAt,
    LocalDateTime settledAt,
    LocalDateTime createdAt
) {
}
