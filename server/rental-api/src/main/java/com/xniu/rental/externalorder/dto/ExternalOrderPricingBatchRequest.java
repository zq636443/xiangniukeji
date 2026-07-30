package com.xniu.rental.externalorder.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ExternalOrderPricingBatchRequest(
    @Valid @NotNull(message = "缺少批量筛选条件") ExternalOrderPricingFilterRequest filter,
    @Valid @NotNull(message = "缺少续租调价规则") ExternalOrderPricingAdjustmentRequest adjustment,
    @Min(value = 0, message = "预期命中数量不能小于 0") Integer expectedMatchedCount
) {
}
