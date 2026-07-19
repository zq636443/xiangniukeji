package com.xniu.rental.pay.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record PaymentRefundRequest(
    @NotNull(message = "请输入退款金额")
    @DecimalMin(value = "0.01", message = "退款金额必须大于 0")
    BigDecimal refundAmount
) {
}
