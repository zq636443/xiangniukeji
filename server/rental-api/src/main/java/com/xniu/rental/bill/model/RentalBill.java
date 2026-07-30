package com.xniu.rental.bill.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RentalBill(
    Long id,
    String billNo,
    Long orderId,
    Long userAccountId,
    Long merchantId,
    Long storeId,
    BillType billType,
    Integer periodNo,
    BillStatus billStatus,
    LocalDateTime dueAt,
    BigDecimal payableAmount,
    BigDecimal paidAmount,
    BigDecimal overdueAmount,
    LocalDateTime paidAt,
    LocalDateTime cancelledAt,
    String remark,
    String generatedBatchNo,
    String renewalChargeMode,
    Integer renewalDays,
    BigDecimal renewalUnitPrice,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
