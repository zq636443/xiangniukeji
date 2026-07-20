package com.xniu.rental.settlement.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SettlementProfitRule(
    Long id,
    String ruleCode,
    String ruleName,
    RuleScope ruleScope,
    String sourceChannel,
    Integer priority,
    Long skuId,
    Long merchantId,
    Long storeId,
    Long storeSkuId,
    BigDecimal channelFeeRate,
    BigDecimal platformFeeRate,
    BigDecimal storeOperationRate,
    BigDecimal maintenanceFundRate,
    BigDecimal channelReferralRate,
    BigDecimal investorShareRate,
    LocalDateTime effectiveAt,
    LocalDateTime expiredAt,
    SettlementRuleStatus status
) {
}
