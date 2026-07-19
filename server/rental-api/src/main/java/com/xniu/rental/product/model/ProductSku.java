package com.xniu.rental.product.model;

public record ProductSku(
    Long id,
    String skuCode,
    Long categoryId,
    String skuName,
    SkuType skuType,
    String description,
    Boolean needFrameAsset,
    Boolean needBatteryAsset,
    Boolean supportCrossStoreReturn,
    ProductStatus status
) {
}
