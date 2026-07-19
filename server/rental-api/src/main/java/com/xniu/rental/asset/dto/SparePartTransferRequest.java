package com.xniu.rental.asset.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record SparePartTransferRequest(
    @NotNull(message = "请选择配件") Long partId,
    @NotNull(message = "请选择调出门店") Long fromStoreId,
    @NotNull(message = "请选择调入门店") Long toStoreId,
    @NotNull(message = "请输入调拨数量") Integer quantity,
    @DecimalMin(value = "0.00", message = "单价不能小于 0") BigDecimal unitPrice,
    String remark
) {
}
