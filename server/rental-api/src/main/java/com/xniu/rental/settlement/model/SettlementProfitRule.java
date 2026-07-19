package com.xniu.rental.settlement.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SettlementProfitRule(
    Long id,
    String ruleCode,
    String ruleName,
    RuleScope ruleScope,
    Long skuId,
    Long merchantId,
    Long storeId,
    Long storeSkuId,
    BigDecimal merchantOrderFeeAmount,
    BigDecimal merchantRentShareRate,
    BigDecimal platformRentShareRate,
    BigDecimal investorRentShareRate,
    LocalDateTime effectiveAt,
    LocalDateTime expiredAt,
    SettlementRuleStatus status
) {
}
