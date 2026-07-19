package com.xniu.rental.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PackageRequest(
    @NotNull(message = "请选择 SKU") Long skuId,
    @NotBlank(message = "请输入套餐名称") String packageName,
    @NotBlank(message = "请选择租期单位") String leaseUnit,
    @NotNull(message = "请输入租期值") Integer leaseValue,
    @NotNull(message = "请输入总期数") Integer totalPeriods,
    @NotBlank(message = "请选择账单日规则") String billDayMode,
    Integer billDay
) {
}
