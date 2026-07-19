package com.xniu.rental.ops.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record ReconciliationBatchResponse(
    Long id,
    String batchNo,
    String channel,
    LocalDate billDate,
    String batchStatus,
    BigDecimal platformTotalAmount,
    BigDecimal channelTotalAmount,
    Integer diffCount,
    String remark,
    Long createdBy,
    LocalDateTime createdAt,
    LocalDateTime finishedAt
) {
}
