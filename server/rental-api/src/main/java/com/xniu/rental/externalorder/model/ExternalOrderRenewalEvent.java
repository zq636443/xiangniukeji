package com.xniu.rental.externalorder.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ExternalOrderRenewalEvent(
    Long id,
    Long externalOrderId,
    String eventNo,
    Integer periodNo,
    LocalDateTime periodStartAt,
    LocalDateTime periodEndAt,
    BigDecimal renewalAmount,
    BigDecimal systemRenewalAmount,
    BigDecimal batteryCostAmount,
    Long settlementSnapshotId,
    String eventStatus,
    ExternalOrderRenewalSource renewalSource,
    Long operatorAccountId,
    String remark,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
