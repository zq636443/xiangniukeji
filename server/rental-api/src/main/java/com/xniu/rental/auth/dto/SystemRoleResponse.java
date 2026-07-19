package com.xniu.rental.auth.dto;

import java.time.LocalDateTime;
import java.util.List;

public record SystemRoleResponse(
    Long id,
    String roleCode,
    String roleName,
    String roleScope,
    String status,
    LocalDateTime createdAt,
    List<String> permissions
) {
}
