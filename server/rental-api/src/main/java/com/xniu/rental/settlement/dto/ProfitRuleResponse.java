package com.xniu.rental.settlement.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProfitRuleResponse(
    Long id,
    String ruleCode,
    String ruleName,
    String ruleScope,
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
    String status
) {
}
