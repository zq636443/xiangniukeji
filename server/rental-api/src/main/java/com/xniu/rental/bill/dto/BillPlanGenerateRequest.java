package com.xniu.rental.bill.dto;

import jakarta.validation.constraints.NotNull;

public record BillPlanGenerateRequest(
    @NotNull(message = "请选择订单") Long orderId,
    String remark
) {
}
