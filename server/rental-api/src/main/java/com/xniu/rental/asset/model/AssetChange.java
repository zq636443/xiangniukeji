package com.xniu.rental.asset.model;

import java.time.LocalDateTime;

public record AssetChange(
    Long id,
    String changeNo,
    Long orderId,
    Long merchantId,
    Long storeId,
    AssetType assetType,
    Long oldAssetId,
    Long newAssetId,
    AssetStatus oldAssetResultStatus,
    Long operatorAccountId,
    String remark,
    LocalDateTime createdAt
) {
}
