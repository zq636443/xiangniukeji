package com.xniu.rental.product.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record StoreSkuPackageRequest(
    @NotNull(message = "请选择 SKU") Long packageId,
    BigDecimal rentalAmount,
    @NotNull(message = "请输入每期金额") BigDecimal periodAmount,
    @NotNull(message = "请输入押金") BigDecimal depositAmount,
    Boolean autoRenewEnabled,
    String renewalUnit,
    Integer renewalValue,
    BigDecimal renewalAmount
) {
}
