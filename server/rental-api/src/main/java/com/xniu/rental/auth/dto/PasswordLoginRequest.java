package com.xniu.rental.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record PasswordLoginRequest(
    @NotBlank(message = "请输入账号") String username,
    @NotBlank(message = "请输入密码") String password
) {
}
