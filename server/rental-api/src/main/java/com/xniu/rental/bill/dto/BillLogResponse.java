package com.xniu.rental.bill.dto;

import java.time.LocalDateTime;

public record BillLogResponse(
    Long id,
    Long billId,
    String fromStatus,
    String toStatus,
    String operationType,
    Long operatorAccountId,
    String remark,
    LocalDateTime createdAt
) {
}
