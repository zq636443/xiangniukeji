package com.xniu.rental.asset.dto;

import jakarta.validation.constraints.NotNull;

public record AssetTransferRequest(
    @NotNull(message = "请选择商户") Long merchantId,
    @NotNull(message = "请选择门店") Long storeId,
    String remark
) {
}
