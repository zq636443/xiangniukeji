package com.xniu.rental.settlement.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record StoreProfitRuleUpdateRequest(
    @NotNull(message = "请输入渠道核销扣点") BigDecimal channelFeeRate,
    @NotNull(message = "请输入租赁平台扣点") BigDecimal platformFeeRate,
    @NotNull(message = "请输入门店运营比例") BigDecimal storeOperationRate,
    @NotNull(message = "请输入维修基金比例") BigDecimal maintenanceFundRate,
    @NotNull(message = "请输入渠道引流比例") BigDecimal channelReferralRate,
    @NotNull(message = "请输入出资方比例") BigDecimal investorShareRate
) {
}
