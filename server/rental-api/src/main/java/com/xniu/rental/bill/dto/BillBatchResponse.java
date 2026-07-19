package com.xniu.rental.bill.dto;

import java.time.LocalDateTime;

public record BillBatchResponse(
    Long id,
    String batchNo,
    String generationType,
    Long orderId,
    Integer generatedCount,
    String remark,
    LocalDateTime createdAt
) {
}
