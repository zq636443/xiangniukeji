package com.xniu.rental.merchant.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MerchantStore(
    Long id,
    Long merchantId,
    String storeCode,
    String storeName,
    String address,
    String businessHours,
    BigDecimal longitude,
    BigDecimal latitude,
    String qrContent,
    StoreStatus status,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
