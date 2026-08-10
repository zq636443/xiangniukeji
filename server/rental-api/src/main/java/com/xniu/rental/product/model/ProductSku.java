package com.xniu.rental.product.model;

import java.math.BigDecimal;

public record ProductSku(
    Long id,
    String skuCode,
    Long categoryId,
    String skuName,
    SkuType skuType,
    String description,
    BigDecimal batteryCostDailyAmount,
    BigDecimal batteryCostMonthlyAmount,
    Boolean needFrameAsset,
    Boolean needBatteryAsset,
    Boolean supportCrossStoreReturn,
    ProductStatus status
) {
}
