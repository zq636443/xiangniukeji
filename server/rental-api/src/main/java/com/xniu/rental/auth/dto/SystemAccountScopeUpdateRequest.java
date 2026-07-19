package com.xniu.rental.auth.dto;

import java.util.List;

public record SystemAccountScopeUpdateRequest(
    List<Long> storeIds
) {
}
