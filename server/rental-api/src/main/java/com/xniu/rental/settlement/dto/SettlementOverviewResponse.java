package com.xniu.rental.settlement.dto;

import java.math.BigDecimal;

public record SettlementOverviewResponse(
    String statementMonth,
    BigDecimal totalPaidRentAmount,
    BigDecimal totalSignFeeAmount,
    BigDecimal totalMerchantPayableAmount,
    BigDecimal totalInvestorPayableAmount,
    BigDecimal totalOperationFeeAmount,
    BigDecimal totalBatteryCostAmount,
    BigDecimal totalMaintenanceDeductAmount,
    BigDecimal totalOpenOverdueAmount,
    Integer merchantStatementCount,
    Integer investorStatementCount
) {
}
