package com.xniu.rental.pay.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record FundAuthUnfreezeRequest(
    @NotNull(message = "请输入解冻金额")
    @DecimalMin(value = "0.01", message = "解冻金额必须大于 0")
    BigDecimal amount,
    String remark
) {
}
