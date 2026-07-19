package com.xniu.rental.asset.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record SparePartStockAdjustRequest(
    Long storeId,
    @NotNull(message = "请输入数量") Integer quantity,
    BigDecimal unitPrice,
    String remark
) {
}
