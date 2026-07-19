package com.xniu.rental.order.dto;

import java.time.LocalDateTime;

public record OrderLogResponse(
    Long id,
    Long orderId,
    String fromStatus,
    String toStatus,
    String operationType,
    Long operatorAccountId,
    String remark,
    LocalDateTime createdAt
) {
}
