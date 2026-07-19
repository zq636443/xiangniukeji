package com.xniu.rental.settlement.dto;

public record SettlementStatementGenerateResponse(
    String statementMonth,
    Integer merchantStatementCount,
    Integer investorStatementCount
) {
}
