package com.xniu.rental.externalorder.dto;

import java.time.LocalDateTime;

public record ExternalRentalOrderLogResponse(
    Long id,
    Long externalOrderId,
    String fromStatus,
    String toStatus,
    String operationType,
    Long operatorAccountId,
    String remark,
    LocalDateTime createdAt
) {
}
