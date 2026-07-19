package com.xniu.rental.asset.dto;

import java.time.LocalDateTime;

public record AssetHandoverResponse(
    Long id,
    String handoverNo,
    Long orderId,
    Long merchantId,
    Long storeId,
    Long userAccountId,
    String handoverType,
    Long frameAssetId,
    Long batteryAssetId,
    String frameResultStatus,
    String batteryResultStatus,
    Long operatorAccountId,
    String remark,
    LocalDateTime createdAt
) {
}
