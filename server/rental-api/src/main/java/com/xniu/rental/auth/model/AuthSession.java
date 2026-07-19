package com.xniu.rental.auth.model;

import java.time.LocalDateTime;

public record AuthSession(
    String token,
    Long accountId,
    AccountType accountType,
    LocalDateTime expiresAt,
    LocalDateTime revokedAt
) {
    public boolean isActive(LocalDateTime now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }
}

