package com.xniu.rental.asset.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AssetRentalBillResponse(
    Long id,
    String billNo,
    String billType,
    Integer periodNo,
    String billStatus,
    LocalDateTime dueAt,
    BigDecimal payableAmount,
    BigDecimal paidAmount,
    BigDecimal overdueAmount
) {
}
