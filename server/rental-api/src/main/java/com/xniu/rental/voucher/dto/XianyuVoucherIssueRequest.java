package com.xniu.rental.voucher.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record XianyuVoucherIssueRequest(
    @NotBlank(message = "请输入闲鱼核销码") String voucherCode,
    @NotNull(message = "请选择门店商品") Long storeSkuId,
    @NotNull(message = "请选择 SKU") Long packageId,
    @NotNull(message = "请输入闲鱼成交金额") @DecimalMin(value = "0.00", message = "成交金额不能小于 0") BigDecimal voucherAmount,
    String voucherTitle
) {
}
