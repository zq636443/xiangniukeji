package com.xniu.rental.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record SystemAccountResetPasswordRequest(
    @NotBlank(message = "请输入新密码") String password
) {
}
