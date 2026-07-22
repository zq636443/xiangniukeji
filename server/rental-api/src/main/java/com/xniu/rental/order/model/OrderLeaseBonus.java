package com.xniu.rental.order.model;

import java.time.LocalDateTime;

public record OrderLeaseBonus(
    Long id,
    Long orderId,
    OrderLeaseBonusType bonusType,
    Integer bonusDays,
    Long operatorAccountId,
    String remark,
    LocalDateTime expectedReturnBefore,
    LocalDateTime expectedReturnAfter,
    LocalDateTime createdAt
) {
}
