package com.xniu.rental.product.dto;

public record SkuResponse(
    Long id,
    String skuCode,
    Long categoryId,
    String categoryName,
    String skuName,
    String skuType,
    String description,
    Boolean needFrameAsset,
    Boolean needBatteryAsset,
    Boolean supportCrossStoreReturn,
    String status
) {
}
