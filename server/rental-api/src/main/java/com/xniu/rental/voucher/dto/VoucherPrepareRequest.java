package com.xniu.rental.voucher.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record VoucherPrepareRequest(
    @NotBlank(message = "请选择来源平台") String sourcePlatform,
    @NotBlank(message = "请输入券码") String voucherCode,
    @NotNull(message = "请选择门店商品") Long storeSkuId,
    @NotNull(message = "请选择 SKU") Long packageId,
    @DecimalMin(value = "0.00", message = "核销金额不能小于 0") BigDecimal verificationAmount
) {
}
