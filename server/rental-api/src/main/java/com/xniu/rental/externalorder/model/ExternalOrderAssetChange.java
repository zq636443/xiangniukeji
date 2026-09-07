package com.xniu.rental.externalorder.model;

import com.xniu.rental.asset.model.AssetStatus;
import com.xniu.rental.asset.model.AssetType;
import java.time.LocalDateTime;

public record ExternalOrderAssetChange(
    Long id,
    String changeNo,
    Long externalOrderId,
    Long merchantId,
    Long storeId,
    AssetType assetType,
    Long oldAssetId,
    String oldAssetSerialNo,
    Long oldInvestorId,
    Long newAssetId,
    String newAssetSerialNo,
    Long newInvestorId,
    AssetStatus oldAssetResultStatus,
    LocalDateTime effectiveAt,
    Long operatorAccountId,
    String remark,
    LocalDateTime createdAt
) {
}
