package com.xniu.rental.auth.dto;

import java.time.LocalDateTime;

public record LoginResponse(
    String token,
    LocalDateTime expiresAt,
    CurrentAccountResponse account
) {
}
