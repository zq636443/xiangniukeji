package com.xniu.rental.ops.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ReconciliationRequest(
    @NotBlank(message = "请选择渠道") String channel,
    @NotNull(message = "请选择账单日期") LocalDate billDate,
    BigDecimal channelTotalAmount,
    String remark
) {
}
