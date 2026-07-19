package com.xniu.rental.merchant.model;

import java.time.LocalDateTime;

public record Merchant(
    Long id,
    String merchantCode,
    String merchantName,
    String contactName,
    String contactPhone,
    String businessLicenseNo,
    MerchantStatus status,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
