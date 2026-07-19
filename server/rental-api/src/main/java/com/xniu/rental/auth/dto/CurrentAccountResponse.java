package com.xniu.rental.auth.dto;

import java.util.List;

public record CurrentAccountResponse(
    Long id,
    String accountType,
    String username,
    String phone,
    String alipayUserId,
    String displayName,
    Long merchantId,
    Long storeId,
    Long investorId,
    List<String> roles,
    List<String> permissions,
    List<StoreScopeResponse> storeScopes
) {
}
