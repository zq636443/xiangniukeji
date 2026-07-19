package com.xniu.rental.pay.dto;

import jakarta.validation.constraints.NotNull;

public record PaymentCreateRequest(
    @NotNull(message = "请选择账单") Long billId
) {
}
