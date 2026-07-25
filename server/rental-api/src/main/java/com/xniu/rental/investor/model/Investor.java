package com.xniu.rental.investor.model;

import java.time.LocalDateTime;

public record Investor(
    Long id,
    String investorCode,
    String investorName,
    String contactName,
    String contactPhone,
    InvestorStatus status,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
