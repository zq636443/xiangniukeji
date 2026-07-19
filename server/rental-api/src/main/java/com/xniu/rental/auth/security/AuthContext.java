package com.xniu.rental.auth.security;

public final class AuthContext {

    private static final ThreadLocal<CurrentAccount> CURRENT = new ThreadLocal<>();

    private AuthContext() {
    }

    public static void set(CurrentAccount account) {
        CURRENT.set(account);
    }

    public static CurrentAccount get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
