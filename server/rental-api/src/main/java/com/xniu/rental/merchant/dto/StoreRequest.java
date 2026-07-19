package com.xniu.rental.merchant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record StoreRequest(
    @NotNull(message = "请选择商户") Long merchantId,
    @NotBlank(message = "请输入门店名称") String storeName,
    @NotBlank(message = "请输入门店地址") String address,
    String businessHours,
    BigDecimal longitude,
    BigDecimal latitude
) {
}
