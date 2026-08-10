package com.xniu.rental.product.dto;

import java.math.BigDecimal;

public record SkuResponse(
    Long id,
    String skuCode,
    Long categoryId,
    String categoryName,
    String skuName,
    String skuType,
    String description,
    BigDecimal batteryCostDailyAmount,
    BigDecimal batteryCostMonthlyAmount,
    Boolean needFrameAsset,
    Boolean needBatteryAsset,
    Boolean supportCrossStoreReturn,
    String status
) {
}
