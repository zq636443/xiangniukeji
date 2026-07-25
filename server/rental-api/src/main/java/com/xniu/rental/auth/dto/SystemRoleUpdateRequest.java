package com.xniu.rental.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record SystemRoleUpdateRequest(
    @NotBlank(message = "请输入角色名称") String roleName,
    @NotBlank(message = "请选择角色状态") String status,
    @NotNull(message = "请选择角色权限") List<String> permissionCodes
) {
}
