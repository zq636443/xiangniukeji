package com.xniu.rental.asset.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record AssetMaintenancePartRequest(
    @NotNull(message = "请选择配件") Long partId,
    @Min(value = 1, message = "消耗数量必须大于 0") Integer quantity,
    BigDecimal unitPrice,
    String remark
) {
}
