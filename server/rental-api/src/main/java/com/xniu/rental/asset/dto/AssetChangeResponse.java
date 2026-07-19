package com.xniu.rental.asset.dto;

import java.time.LocalDateTime;

public record AssetChangeResponse(
    Long id,
    String changeNo,
    Long orderId,
    Long merchantId,
    Long storeId,
    String assetType,
    Long oldAssetId,
    Long newAssetId,
    String oldAssetResultStatus,
    Long operatorAccountId,
    String remark,
    LocalDateTime createdAt
) {
}
