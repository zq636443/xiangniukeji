package com.xniu.rental.settlement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProfitRuleRequest(
    @NotBlank(message = "请输入规则名称") String ruleName,
    @NotBlank(message = "请选择规则范围") String ruleScope,
    String sourceChannel,
    Integer priority,
    Long skuId,
    Long merchantId,
    Long storeId,
    Long storeSkuId,
    @NotNull(message = "请输入渠道核销扣点") BigDecimal channelFeeRate,
    @NotNull(message = "请输入租赁平台扣点") BigDecimal platformFeeRate,
    @NotNull(message = "请输入门店运营比例") BigDecimal storeOperationRate,
    @NotNull(message = "请输入门店维修分润比例") BigDecimal maintenanceFundRate,
    @NotNull(message = "请输入渠道引流比例") BigDecimal channelReferralRate,
    @NotNull(message = "请输入出资方比例") BigDecimal investorShareRate,
    LocalDateTime effectiveAt,
    LocalDateTime expiredAt
) {
}
