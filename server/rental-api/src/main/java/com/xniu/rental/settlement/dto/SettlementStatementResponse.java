package com.xniu.rental.settlement.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SettlementStatementResponse(
    Long id,
    String statementNo,
    String statementMonth,
    String beneficiaryType,
    Long beneficiaryId,
    Long merchantId,
    Long storeId,
    BigDecimal rentBaseAmount,
    BigDecimal signFeeIncomeAmount,
    BigDecimal rentShareIncomeAmount,
    BigDecimal operationFeeAmount,
    BigDecimal maintenanceDeductAmount,
    BigDecimal adjustmentAmount,
    BigDecimal payableAmount,
    Integer orderCount,
    Integer billCount,
    String status,
    LocalDateTime generatedAt,
    LocalDateTime confirmedAt,
    LocalDateTime paidAt,
    String remark,
    Integer lineCount
) {
}
