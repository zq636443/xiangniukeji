package com.xniu.rental.pay.model;

public enum FundAuthStatus {
    CREATED,
    AUTHORIZING,
    AUTHORIZED,
    FAILED,
    CANCELLED,
    UNFROZEN,
    CAPTURED,
    CLOSED
}
