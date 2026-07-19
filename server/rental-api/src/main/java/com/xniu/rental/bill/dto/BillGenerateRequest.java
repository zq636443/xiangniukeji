package com.xniu.rental.bill.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BillGenerateRequest(
    @NotNull(message = "请选择订单") Long orderId,
    @NotBlank(message = "请选择账单类型") String billType,
    Integer periodNo,
    BigDecimal overdueAmount,
    LocalDateTime dueAt,
    String remark
) {
}
