package com.xniu.rental.order.dto;

import java.util.List;

public record OrderBatchImportResponse(
    Integer totalCount,
    Integer successCount,
    Integer failedCount,
    List<OrderBatchImportRowResultResponse> results
) {
}
