package com.xniu.rental.externalorder.model;

import java.time.LocalDateTime;

public record ExternalRentalOrderLog(
    Long id,
    Long externalOrderId,
    ExternalRentalOrderStatus fromStatus,
    ExternalRentalOrderStatus toStatus,
    ExternalOrderOperationType operationType,
    Long operatorAccountId,
    String remark,
    LocalDateTime createdAt
) {
}
