package com.xniu.rental.settlement.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProfitRuleResponse(
    Long id,
    String ruleCode,
    String ruleName,
    String ruleScope,
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
    String status
) {
}
