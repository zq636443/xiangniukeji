package com.xniu.rental.order.dto;

import jakarta.validation.constraints.NotBlank;

public record OrderExceptionRequest(
    @NotBlank(message = "请输入异常原因") String reason
) {
}
