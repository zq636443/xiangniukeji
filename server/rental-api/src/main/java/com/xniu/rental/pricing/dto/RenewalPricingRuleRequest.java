package com.xniu.rental.pricing.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record RenewalPricingRuleRequest(
    @NotNull(message = "请选择是否开启自动续租") Boolean autoRenewEnabled,
    String renewalUnit,
    Integer renewalValue,
    BigDecimal renewalAmount,
    @NotBlank(message = "请选择续租计费模式") String renewalBillingMode,
    BigDecimal renewalDailyAmount,
    Boolean renewalDailyCapEnabled,
    @Min(value = 0, message = "宽限时间不能小于 0")
    @Max(value = 72, message = "宽限时间不能超过 72 小时")
    Integer renewalGraceHours,
    BigDecimal overdueDailyAmount,
    @NotBlank(message = "请输入调价原因") String reason
) {
}
