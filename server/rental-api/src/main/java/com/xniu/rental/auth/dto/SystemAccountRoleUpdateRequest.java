package com.xniu.rental.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record SystemAccountRoleUpdateRequest(
    @NotBlank(message = "请选择角色") String roleCode
) {
}
