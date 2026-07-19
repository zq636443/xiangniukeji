package com.xniu.rental.auth.model;

import java.time.LocalDateTime;

public record Account(
    Long id,
    AccountType accountType,
    String username,
    String phone,
    String alipayUserId,
    String displayName,
    String passwordHash,
    Long merchantId,
    Long storeId,
    Long investorId,
    AccountStatus status,
    LocalDateTime lastLoginAt
) {
}

