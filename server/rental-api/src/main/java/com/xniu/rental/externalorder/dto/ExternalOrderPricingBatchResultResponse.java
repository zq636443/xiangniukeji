package com.xniu.rental.externalorder.dto;

import java.util.List;

public record ExternalOrderPricingBatchResultResponse(
    String batchNo,
    Integer matchedCount,
    Integer successCount,
    Integer pendingConfirmationCount,
    Integer unchangedCount,
    Integer skippedInactiveCount,
    Integer failedCount,
    List<RowResult> results
) {
    public record RowResult(
        Long externalOrderId,
        String recordNo,
        Boolean success,
        String revisionStatus,
        String message
    ) {
    }
}
