package com.xniu.rental.bill.dto;

import java.util.List;

public record BillGenerationResultResponse(
    BillBatchResponse batch,
    List<BillResponse> bills
) {
}
