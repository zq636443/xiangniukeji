package com.xniu.rental.overdue.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OverdueCase(
    Long id,
    String caseNo,
    String statMonth,
    Long orderId,
    Long billId,
    Long userAccountId,
    Long merchantId,
    Long storeId,
    Long storeSkuId,
    Long skuId,
    BigDecimal overdueAmount,
    BigDecimal unpaidAmount,
    Integer failCount,
    String lastFailReason,
    LocalDateTime lastDeductAt,
    OverdueStatus overdueStatus,
    CollectionStatus collectionStatus,
    String collectionRemark,
    LocalDateTime resolvedAt,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
