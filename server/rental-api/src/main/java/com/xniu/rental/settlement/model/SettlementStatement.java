package com.xniu.rental.settlement.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SettlementStatement(
    Long id,
    String statementNo,
    String statementMonth,
    StatementBeneficiaryType beneficiaryType,
    Long beneficiaryId,
    Long merchantId,
    Long storeId,
    BigDecimal rentBaseAmount,
    BigDecimal signFeeIncomeAmount,
    BigDecimal rentShareIncomeAmount,
    BigDecimal operationFeeAmount,
    BigDecimal batteryCostAmount,
    BigDecimal maintenanceDeductAmount,
    BigDecimal adjustmentAmount,
    BigDecimal payableAmount,
    Integer orderCount,
    Integer billCount,
    SettlementStatementStatus status,
    LocalDateTime generatedAt,
    LocalDateTime confirmedAt,
    LocalDateTime paidAt,
    String remark,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
