package com.xniu.rental.product.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

public record StoreSkuRequest(
    @NotNull(message = "请选择商户") Long merchantId,
    @NotNull(message = "请选择门店") Long storeId,
    @NotNull(message = "请选择 SKU") Long skuId,
    @NotBlank(message = "请输入门店商品名称") String displayName,
    @NotBlank(message = "请选择售卖模式") String saleMode,
    @NotNull(message = "请输入签单费") BigDecimal signFeeAmount,
    @NotBlank(message = "请选择签单费承担方") String signFeePayer,
    @Valid @NotEmpty(message = "请至少配置一个套餐价格") List<StoreSkuPackageRequest> packages
) {
}
