package com.xniu.rental.order.dto;

import jakarta.validation.constraints.NotBlank;

public record OrderCancelRequest(
    @NotBlank(message = "请输入取消原因") String reason
) {
}
