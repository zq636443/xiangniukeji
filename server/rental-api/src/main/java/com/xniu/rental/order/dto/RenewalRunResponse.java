package com.xniu.rental.order.dto;

public record RenewalRunResponse(
    Integer scannedCount,
    Integer generatedCount,
    Long batchId,
    String batchNo
) {
}
