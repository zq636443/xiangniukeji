package com.xniu.rental.auth.model;

public record StoreScope(
    Long merchantId,
    Long storeId,
    StoreScopeType scopeType
) {
}

