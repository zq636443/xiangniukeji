package com.xniu.rental.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record PackageRequest(
    @NotNull(message = "请选择商品链接") Long skuId,
    @NotBlank(message = "请输入 SKU 名称") String packageName,
    @NotNull(message = "请输入 SKU 价格") BigDecimal priceAmount,
    BigDecimal signFeeAmount,
    @NotBlank(message = "请选择租期单位") String leaseUnit,
    @NotNull(message = "请输入租期值") Integer leaseValue,
    @NotNull(message = "请输入总期数") Integer totalPeriods,
    @NotBlank(message = "请选择账单日规则") String billDayMode,
    Integer billDay
) {
    public PackageRequest(
        Long skuId,
        String packageName,
        BigDecimal priceAmount,
        String leaseUnit,
        Integer leaseValue,
        Integer totalPeriods,
        String billDayMode,
        Integer billDay
    ) {
        this(skuId, packageName, priceAmount, null, leaseUnit, leaseValue, totalPeriods, billDayMode, billDay);
    }
}
