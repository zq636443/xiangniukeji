package com.xniu.rental.investor.dto;

import jakarta.validation.constraints.NotBlank;

public record InvestorAccountCreateRequest(
    @NotBlank(message = "请输入出资方账号登录账号") String username,
    @NotBlank(message = "请输入出资方账号显示名称") String displayName,
    @NotBlank(message = "请输入出资方账号手机号") String phone,
    @NotBlank(message = "请输入出资方账号初始密码") String password
) {
}
