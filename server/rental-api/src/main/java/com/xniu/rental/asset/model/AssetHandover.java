package com.xniu.rental.asset.model;

import java.time.LocalDateTime;

public record AssetHandover(
    Long id,
    String handoverNo,
    Long orderId,
    Long merchantId,
    Long storeId,
    Long userAccountId,
    HandoverType handoverType,
    Long frameAssetId,
    Long batteryAssetId,
    AssetStatus frameResultStatus,
    AssetStatus batteryResultStatus,
    Long operatorAccountId,
    String remark,
    LocalDateTime createdAt
) {
}
