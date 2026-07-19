package com.xniu.rental.product.model;

import java.math.BigDecimal;

public record StoreSku(
    Long id,
    Long merchantId,
    Long storeId,
    Long skuId,
    String storeSkuCode,
    SkuType saleMode,
    String displayName,
    BigDecimal signFeeAmount,
    SignFeePayer signFeePayer,
    StoreSkuStatus status
) {
}
