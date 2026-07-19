package com.xniu.rental.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record SystemAccountUpdateRequest(
    String username,
    @NotBlank(message = "请输入显示名称") String displayName,
    @NotBlank(message = "请输入手机号") String phone
) {
}
