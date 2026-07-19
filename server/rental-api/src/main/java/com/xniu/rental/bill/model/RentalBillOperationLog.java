package com.xniu.rental.bill.model;

import java.time.LocalDateTime;

public record RentalBillOperationLog(
    Long id,
    Long billId,
    BillStatus fromStatus,
    BillStatus toStatus,
    BillOperationType operationType,
    Long operatorAccountId,
    String remark,
    LocalDateTime createdAt
) {
}
