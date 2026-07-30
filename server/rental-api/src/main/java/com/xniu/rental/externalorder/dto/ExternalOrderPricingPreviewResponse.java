package com.xniu.rental.externalorder.dto;

public record ExternalOrderPricingPreviewResponse(
    Integer matchedCount,
    Integer eligibleCount,
    Integer unchangedCount,
    Integer immediateApplyCount,
    Integer confirmedApplyCount,
    Integer pendingConfirmationCount,
    Integer blockedPendingCount,
    Integer skippedInactiveCount
) {
}
