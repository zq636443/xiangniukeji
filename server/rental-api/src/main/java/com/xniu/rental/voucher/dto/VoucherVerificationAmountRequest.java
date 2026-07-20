package com.xniu.rental.voucher.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record VoucherVerificationAmountRequest(
    @NotNull(message = "请输入核销金额")
    @DecimalMin(value = "0.00", message = "核销金额不能小于 0")
    BigDecimal verificationAmount
) {
}
