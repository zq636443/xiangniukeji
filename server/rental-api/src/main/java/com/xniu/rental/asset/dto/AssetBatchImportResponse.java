package com.xniu.rental.asset.dto;

import java.util.List;

public record AssetBatchImportResponse(
    Integer totalCount,
    Integer successCount,
    Integer failedCount,
    List<AssetBatchImportRowResultResponse> results
) {
}
