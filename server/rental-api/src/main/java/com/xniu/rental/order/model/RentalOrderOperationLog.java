package com.xniu.rental.order.model;

import java.time.LocalDateTime;

public record RentalOrderOperationLog(
    Long id,
    Long orderId,
    OrderStatus fromStatus,
    OrderStatus toStatus,
    OrderOperationType operationType,
    Long operatorAccountId,
    String remark,
    LocalDateTime createdAt
) {
}
