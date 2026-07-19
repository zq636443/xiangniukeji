package com.xniu.rental.pay.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record FundAuthCreateRequest(
    @NotNull(message = "请选择订单") Long orderId,
    @NotNull(message = "请输入授权金额")
    @DecimalMin(value = "0.01", message = "授权金额必须大于 0")
    BigDecimal authAmount
) {
}
