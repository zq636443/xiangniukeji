package com.xniu.rental.externalorder.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ExternalOrderManualRenewalRequest(
    @NotNull(message = "续租起点已失效，请刷新订单后重试") LocalDateTime expectedPeriodStartAt,
    @NotNull(message = "请输入本次续租结束时间") LocalDateTime periodEndAt,
    @NotNull(message = "请输入本次续租毛额")
    @DecimalMin(value = "0.01", message = "本次续租毛额必须大于 0")
    @Digits(integer = 10, fraction = 2, message = "本次续租毛额最多保留 2 位小数")
    BigDecimal verificationAmount,
    @NotBlank(message = "请填写人工续租备注")
    @Size(max = 255, message = "人工续租备注不能超过 255 个字")
    String remark
) {
}
