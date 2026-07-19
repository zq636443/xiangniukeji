package com.xniu.rental.asset.model;

import java.time.LocalDateTime;

public record AssetStatusLog(
    Long id,
    Long assetId,
    AssetStatus fromStatus,
    AssetStatus toStatus,
    Long operatorAccountId,
    String remark,
    LocalDateTime createdAt
) {
}
