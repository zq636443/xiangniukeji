package com.xniu.rental.pay.dto;

import java.time.LocalDateTime;

public record DeductBatchResponse(
    Long id,
    String batchNo,
    String batchStatus,
    Integer plannedCount,
    Integer successCount,
    Integer failedCount,
    String remark,
    LocalDateTime startedAt,
    LocalDateTime finishedAt,
    LocalDateTime createdAt
) {
}
