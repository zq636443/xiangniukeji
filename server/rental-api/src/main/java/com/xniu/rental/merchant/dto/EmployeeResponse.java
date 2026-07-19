package com.xniu.rental.merchant.dto;

import java.util.List;

public record EmployeeResponse(
    Long id,
    Long merchantId,
    Long storeId,
    String username,
    String displayName,
    String phone,
    String accountType,
    String status,
    List<StoreResponse> authorizedStores
) {
}
