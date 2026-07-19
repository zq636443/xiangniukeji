package com.xniu.rental.contract.dto;

import jakarta.validation.constraints.NotNull;

public record ContractGenerateRequest(
    @NotNull(message = "请选择订单") Long orderId,
    Long templateId
) {
}
