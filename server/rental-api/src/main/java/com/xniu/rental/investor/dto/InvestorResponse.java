package com.xniu.rental.investor.dto;

import java.math.BigDecimal;

public record InvestorResponse(
    Long id,
    String investorCode,
    String investorName,
    String contactName,
    String contactPhone,
    BigDecimal operationFeeRate,
    String status
) {
}
