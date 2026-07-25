package com.xniu.rental.investor.dto;

public record InvestorResponse(
    Long id,
    String investorCode,
    String investorName,
    String contactName,
    String contactPhone,
    String status
) {
}
