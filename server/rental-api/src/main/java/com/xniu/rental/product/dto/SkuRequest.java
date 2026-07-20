package com.xniu.rental.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SkuRequest(
    @NotNull(message = "请选择分类") Long categoryId,
    @NotBlank(message = "请输入链接名称") String skuName,
    @NotBlank(message = "请选择链接类型") String skuType,
    String description,
    Boolean needFrameAsset,
    Boolean needBatteryAsset,
    Boolean supportCrossStoreReturn
) {
}
