package com.xniu.rental.externalorder.dto;

import java.util.List;

public record ExternalRentalOrderBatchImportResponse(
    Integer totalCount,
    Integer successCount,
    Integer failedCount,
    List<ExternalRentalOrderImportRowResultResponse> results
) {
}
