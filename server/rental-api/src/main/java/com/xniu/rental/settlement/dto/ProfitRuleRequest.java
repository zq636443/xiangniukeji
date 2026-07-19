package com.xniu.rental.settlement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProfitRuleRequest(
    @NotBlank(message = "请输入规则名称") String ruleName,
    @NotBlank(message = "请选择规则范围") String ruleScope,
    Long skuId,
    Long merchantId,
    Long storeId,
    Long storeSkuId,
    @NotNull(message = "请输入门店办单费") BigDecimal merchantOrderFeeAmount,
    @NotNull(message = "请输入门店租金分成比例") BigDecimal merchantRentShareRate,
    @NotNull(message = "请输入平台租金分成比例") BigDecimal platformRentShareRate,
    @NotNull(message = "请输入出资方收益比例") BigDecimal investorRentShareRate,
    LocalDateTime effectiveAt,
    LocalDateTime expiredAt
) {
}
