package com.xniu.rental.auth.dto;

public record StoreScopeResponse(
    Long merchantId,
    Long storeId,
    String scopeType
) {
}
