package com.xniu.rental.settlement.dto;

import java.util.List;

public record SettlementEntryGenerateResponse(
    Long orderId,
    Long snapshotId,
    Integer createdCount,
    List<SettlementIncomeEntryResponse> entries
) {
}
