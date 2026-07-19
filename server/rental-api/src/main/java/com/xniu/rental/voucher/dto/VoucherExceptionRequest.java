package com.xniu.rental.voucher.dto;

import jakarta.validation.constraints.NotBlank;

public record VoucherExceptionRequest(
    @NotBlank(message = "请输入异常原因") String reason
) {
}
