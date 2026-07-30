package com.xniu.rental.bill.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record BillResponse(
    Long id,
    String billNo,
    Long orderId,
    Long userAccountId,
    Long merchantId,
    Long storeId,
    String billType,
    Integer periodNo,
    String billStatus,
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
    List<BillItemResponse> items,
    List<BillLogResponse> logs
) {
}
