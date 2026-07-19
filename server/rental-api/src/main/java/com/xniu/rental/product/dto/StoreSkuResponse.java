package com.xniu.rental.product.dto;

import java.math.BigDecimal;
import java.util.List;

public record StoreSkuResponse(
    Long id,
    Long merchantId,
    String merchantName,
    Long storeId,
    String storeName,
    Long skuId,
    String skuName,
    String storeSkuCode,
    String saleMode,
    String displayName,
    BigDecimal signFeeAmount,
    String signFeePayer,
    Boolean needFrameAsset,
    Boolean needBatteryAsset,
    Boolean supportCrossStoreReturn,
    String status,
    List<StoreSkuPackageResponse> packages
) {
}
