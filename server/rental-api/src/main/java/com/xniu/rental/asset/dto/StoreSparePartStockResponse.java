package com.xniu.rental.asset.dto;

import java.math.BigDecimal;

public record StoreSparePartStockResponse(
    Long merchantId,
    String merchantName,
    Long storeId,
    String storeName,
    Long partId,
    String partName,
    Integer stockQuantity,
    BigDecimal avgUnitPrice,
    BigDecimal stockAmount
) {
}
