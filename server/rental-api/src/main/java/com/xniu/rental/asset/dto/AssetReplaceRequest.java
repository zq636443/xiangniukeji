package com.xniu.rental.asset.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AssetReplaceRequest(
    @NotBlank(message = "请选择资产类型") String assetType,
    @NotNull(message = "请选择新资产") Long newAssetId,
    String oldAssetResultStatus,
    String remark
) {
}
