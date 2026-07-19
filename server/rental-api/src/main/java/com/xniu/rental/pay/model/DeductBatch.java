package com.xniu.rental.pay.model;

import java.time.LocalDateTime;

public record DeductBatch(
    Long id,
    String batchNo,
    DeductBatchStatus batchStatus,
    Integer plannedCount,
    Integer successCount,
    Integer failedCount,
    String remark,
    LocalDateTime startedAt,
    LocalDateTime finishedAt,
    LocalDateTime createdAt
) {
}
