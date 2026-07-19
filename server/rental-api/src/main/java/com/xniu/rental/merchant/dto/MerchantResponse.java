package com.xniu.rental.merchant.dto;

public record MerchantResponse(
    Long id,
    String merchantCode,
    String merchantName,
    String contactName,
    String contactPhone,
    String businessLicenseNo,
    String status
) {
}
