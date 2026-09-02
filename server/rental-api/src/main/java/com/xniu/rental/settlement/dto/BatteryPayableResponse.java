package com.xniu.rental.settlement.dto;

import java.math.BigDecimal;

public record BatteryPayableResponse(
    String statementMonth,
    Long storeId,
    BigDecimal initialAmount,
    BigDecimal renewalAmount,
    BigDecimal billAmount,
    BigDecimal totalAmount,
    Integer initialCount,
    Integer renewalCount,
    Integer billCount
) {
}
