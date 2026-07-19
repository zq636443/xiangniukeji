package com.xniu.rental.pay.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record AgreementSignRequest(
    @NotNull(message = "请选择订单") Long orderId,
    @NotNull(message = "请输入单笔最大扣款金额")
    @DecimalMin(value = "0.01", message = "单笔最大扣款金额必须大于 0")
    BigDecimal maxSingleAmount
) {
}
