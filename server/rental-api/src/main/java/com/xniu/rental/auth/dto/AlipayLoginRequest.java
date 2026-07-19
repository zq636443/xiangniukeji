package com.xniu.rental.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record AlipayLoginRequest(
    @NotBlank(message = "缺少支付宝授权码") String authCode,
    String nickName,
    String phone
) {
}
