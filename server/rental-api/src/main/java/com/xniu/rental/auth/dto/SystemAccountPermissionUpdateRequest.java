package com.xniu.rental.auth.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record SystemAccountPermissionUpdateRequest(
    @NotNull(message = "请选择账号权限") List<String> permissionCodes
) {
}
