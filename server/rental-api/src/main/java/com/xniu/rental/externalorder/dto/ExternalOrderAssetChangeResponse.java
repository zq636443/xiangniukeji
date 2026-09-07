package com.xniu.rental.externalorder.dto;

import java.time.LocalDateTime;

public record ExternalOrderAssetChangeResponse(
    Long id,
    String changeNo,
    Long externalOrderId,
    Long merchantId,
    Long storeId,
    String assetType,
    Long oldAssetId,
    String oldAssetSerialNo,
    Long newAssetId,
    String newAssetSerialNo,
    String oldAssetResultStatus,
    LocalDateTime effectiveAt,
    Long operatorAccountId,
    String remark,
    LocalDateTime createdAt
) {
}
