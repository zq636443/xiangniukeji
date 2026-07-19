package com.xniu.rental.merchant.dto;

import java.math.BigDecimal;

public record StoreResponse(
    Long id,
    Long merchantId,
    String storeCode,
    String storeName,
    String address,
    String businessHours,
    BigDecimal longitude,
    BigDecimal latitude,
    String qrContent,
    String status
) {
}
