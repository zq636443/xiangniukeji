package com.xniu.rental.overdue.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OverdueCaseResponse(
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
    String overdueStatus,
    String collectionStatus,
    String collectionRemark,
    LocalDateTime resolvedAt,
    LocalDateTime createdAt,
    List<OverdueCollectionLogResponse> logs
) {
}
