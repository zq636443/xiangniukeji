package com.xniu.rental.auth.dto;

import java.time.LocalDateTime;

public record SystemPermissionResponse(
    Long id,
    String permissionCode,
    String permissionName,
    String moduleCode,
    LocalDateTime createdAt
) {
}
