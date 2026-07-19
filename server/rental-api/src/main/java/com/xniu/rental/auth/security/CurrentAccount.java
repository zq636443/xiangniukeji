package com.xniu.rental.auth.security;

import com.xniu.rental.auth.dto.CurrentAccountResponse;

public record CurrentAccount(
    String token,
    CurrentAccountResponse account
) {

    public boolean hasPermission(String permission) {
        return account.permissions().contains(permission);
    }
}
