package com.xniu.rental.voucher.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record VoucherPrepareRequest(
    @NotBlank(message = "请选择来源平台") String sourcePlatform,
    @NotBlank(message = "请输入券码") String voucherCode,
    @NotNull(message = "请选择门店商品") Long storeSkuId,
    @NotNull(message = "请选择套餐") Long packageId
) {
}
