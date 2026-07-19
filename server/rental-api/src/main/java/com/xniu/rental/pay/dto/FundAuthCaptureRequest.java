package com.xniu.rental.pay.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record FundAuthCaptureRequest(
    Long billId,
    @NotNull(message = "请输入扣费金额")
    @DecimalMin(value = "0.01", message = "扣费金额必须大于 0")
    BigDecimal amount,
    String remark
) {
}
