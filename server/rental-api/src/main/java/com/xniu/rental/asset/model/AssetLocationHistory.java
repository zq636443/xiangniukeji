package com.xniu.rental.asset.model;

import java.time.LocalDateTime;

public record AssetLocationHistory(
    Long id,
    Long assetId,
    Long fromMerchantId,
    Long fromStoreId,
    Long toMerchantId,
    Long toStoreId,
    LocalDateTime movedAt,
    String remark
) {
}
