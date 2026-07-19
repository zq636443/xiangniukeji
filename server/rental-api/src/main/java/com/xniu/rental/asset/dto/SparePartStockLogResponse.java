package com.xniu.rental.asset.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SparePartStockLogResponse(
    Long id,
    Long partId,
    Long merchantId,
    String merchantName,
    Long storeId,
    String storeName,
    String partName,
    String changeType,
    Integer quantityChange,
    BigDecimal unitPrice,
    BigDecimal amount,
    String refType,
    Long refId,
    Long operatorAccountId,
    String remark,
    LocalDateTime createdAt
) {
}
