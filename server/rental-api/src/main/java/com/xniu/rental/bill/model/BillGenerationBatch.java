package com.xniu.rental.bill.model;

import java.time.LocalDateTime;

public record BillGenerationBatch(
    Long id,
    String batchNo,
    BillGenerationType generationType,
    Long orderId,
    Integer generatedCount,
    String remark,
    LocalDateTime createdAt
) {
}
