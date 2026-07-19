package com.xniu.rental.asset.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

public record SparePartRequest(
    @NotBlank(message = "请输入配件名称") String partName,
    String spec,
    @NotBlank(message = "请输入单位") String unit,
    @DecimalMin(value = "0.00", message = "采购价不能小于 0") BigDecimal procurementPrice,
    @DecimalMin(value = "0.00", message = "单价不能小于 0") BigDecimal unitPrice,
    @DecimalMin(value = "0.00", message = "回收价不能小于 0") BigDecimal buybackPrice,
    @Min(value = 0, message = "初始库存不能小于 0") Integer initialQuantity
) {
}
