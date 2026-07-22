package com.xniu.rental.order.dto;

import java.time.LocalDateTime;

public record OrderLeaseBonusResponse(
    Long id,
    Long orderId,
    String bonusType,
    Integer bonusDays,
    Long operatorAccountId,
    String remark,
    LocalDateTime expectedReturnBefore,
    LocalDateTime expectedReturnAfter,
    LocalDateTime createdAt
) {
}
