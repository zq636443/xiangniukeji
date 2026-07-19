package com.xniu.rental.product.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

public record StoreSkuBatchPublishRequest(
    @NotNull(message = "请选择 SKU") Long skuId,
    @NotEmpty(message = "请选择门店") List<Long> storeIds,
    String displayName,
    String saleMode,
    BigDecimal signFeeAmount,
    String signFeePayer,
    @Valid @NotEmpty(message = "请至少配置一个套餐价格") List<StoreSkuPackageRequest> packages
) {
}
