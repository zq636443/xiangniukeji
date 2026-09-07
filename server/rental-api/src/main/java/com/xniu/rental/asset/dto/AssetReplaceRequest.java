package com.xniu.rental.asset.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AssetReplaceRequest(
    @NotBlank(message = "请选择资产类型") String assetType,
    Long expectedOldAssetId,
    @NotNull(message = "请选择新资产") Long newAssetId,
    String oldAssetResultStatus,
    String remark
) {
    /** Keep the formal-order API/source compatibility; supplemental-order
     * replacement explicitly requires expectedOldAssetId in its service. */
    public AssetReplaceRequest(
        String assetType,
        Long newAssetId,
        String oldAssetResultStatus,
        String remark
    ) {
        this(assetType, null, newAssetId, oldAssetResultStatus, remark);
    }
}
