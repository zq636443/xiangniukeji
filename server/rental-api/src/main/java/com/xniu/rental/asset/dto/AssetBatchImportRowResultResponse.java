package com.xniu.rental.asset.dto;

public record AssetBatchImportRowResultResponse(
    Integer lineNo,
    boolean success,
    Long assetId,
    String assetCode,
    String serialNo,
    String message
) {
}
