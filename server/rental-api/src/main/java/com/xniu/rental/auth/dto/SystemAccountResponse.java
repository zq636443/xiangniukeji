package com.xniu.rental.auth.dto;

import java.time.LocalDateTime;
import java.util.List;

public record SystemAccountResponse(
    Long id,
    String accountType,
    String username,
    String phone,
    String displayName,
    Long merchantId,
    String merchantName,
    Long storeId,
    String storeName,
    Long investorId,
    String investorName,
    String status,
    LocalDateTime lastLoginAt,
    LocalDateTime createdAt,
    List<String> roles,
    List<String> permissions,
    List<String> directPermissions,
    List<StoreScopeResponse> storeScopes
) {
}
