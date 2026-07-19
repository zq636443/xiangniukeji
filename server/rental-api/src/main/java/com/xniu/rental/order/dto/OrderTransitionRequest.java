package com.xniu.rental.order.dto;

import jakarta.validation.constraints.NotBlank;

public record OrderTransitionRequest(
    @NotBlank(message = "请选择目标状态") String targetStatus,
    String remark
) {
}
