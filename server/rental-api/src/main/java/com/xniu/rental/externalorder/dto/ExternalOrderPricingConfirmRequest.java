package com.xniu.rental.externalorder.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record ExternalOrderPricingConfirmRequest(
    @NotBlank(message = "请选择客户确认方式") String confirmationMethod,
    @NotBlank(message = "请填写确认凭证或备注")
    @Size(max = 500, message = "确认凭证说明不能超过 500 个字") String confirmationReference,
    LocalDateTime customerConfirmedAt
) {
}
