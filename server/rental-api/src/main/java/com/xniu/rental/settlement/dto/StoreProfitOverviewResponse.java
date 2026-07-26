package com.xniu.rental.settlement.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record StoreProfitOverviewResponse(
    Long statementId,
    String statementNo,
    String statementMonth,
    Long merchantId,
    Long storeId,
    BigDecimal settlementBaseAmount,
    BigDecimal signFeeAmount,
    BigDecimal storeOperationAmount,
    BigDecimal storeMaintenanceAmount,
    BigDecimal maintenanceReimburseAmount,
    BigDecimal maintenanceDeductAmount,
    BigDecimal adjustmentAmount,
    BigDecimal payableAmount,
    Integer orderCount,
    Integer billCount,
    Integer lineCount,
    String status,
    LocalDateTime generatedAt,
    LocalDateTime confirmedAt,
    LocalDateTime paidAt
) {
}
